package org.cleverframe.common.interceptor;

import org.cleverframe.common.attributes.CommonRequestAttributes;
import org.cleverframe.common.exception.ExceptionUtils;
import org.cleverframe.common.spring.SpringBeanNames;
import org.cleverframe.common.spring.SpringContextHolder;
import org.cleverframe.common.time.DateTimeUtils;
import org.cleverframe.common.user.IUserUtils;
import org.cleverframe.common.utils.HttpServletRequestUtils;
import org.cleverframe.common.utils.UserAgentUtils;
import org.cleverframe.common.vo.request.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * 请求统计拦截器，此拦截器是"/**"路径拦截器，需要在Spring中注入该Bean<br/>
 * <p/>
 * 作者：LiZW <br/>
 * 创建时间：2016-5-16 16:12 <br/>
 *
 * @see IRequestStatistics
 * @see RequestInfo
 */
public class StatisticsInterceptor implements HandlerInterceptor {
    /**
     * 日志对象
     */
    private final static Logger logger = LoggerFactory.getLogger(StatisticsInterceptor.class);

    /**
     * 用户信息获取接口
     */
    private static IUserUtils userUtils;

    /**
     * 返回用户信息获取接口
     *
     * @return 获取失败返回null
     */
    public static IUserUtils getUserUtils() {
        if (userUtils == null) {
            userUtils = SpringContextHolder.getBean(SpringBeanNames.UserUtils);
        }
        if (userUtils == null) {
            RuntimeException exception = new RuntimeException("### IUserUtils注入失败,BeanName=[" + SpringBeanNames.UserUtils + "]");
            logger.error(exception.getMessage(), exception);
        }
        return userUtils;
    }

    /**
     * 服务器请求统计服务类，需要在Spring中注入实现类
     */
    private IRequestStatistics requestStatistics;

    public IRequestStatistics getRequestStatistics() {
        return requestStatistics;
    }

    public void setRequestStatistics(IRequestStatistics requestStatistics) {
        this.requestStatistics = requestStatistics;
    }

    /**
     * 请求处理前的回调方法<br/>
     * <p/>
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (null == requestStatistics) {
            logger.error("### StatisticsInterceptor.preHandle 服务所有的请求统计实现类未注入，请注入：StatisticsInterceptor.requestStatistics");
            return true;
        }



        // 设置当前请求请求时间
        requestStatistics.setRequestStartTime(request, response);

        // 服务器本次启动后处理的请求总数 加1
        boolean requestCountByStartFlag = requestStatistics.addRequestCountByStart(request, response);

        // 服务器当天处理请求总数(00:00:00--23:59:59) 加1
        boolean requestCountByDayFlag = requestStatistics.addRequestCountByDay(request, response);

        // 服务器当前小时处理请求总数(n:00:00-n:59:59) 加1
        boolean requestCountByHourFlag = requestStatistics.addRequestCountByHour(request, response);

        // 设置最后一次请求的时间-- 必须在请求数量统计之后调用，否则会导致 RequestCountByDay RequestCountByHour 无法清零
        boolean lastRequestTimeFlag = requestStatistics.setLastRequestTime(request, response);

        if (logger.isDebugEnabled()) {
            String tmp = "\r\n" +
                    "#=======================================================================================================================#\r\n" +
                    "# 请求处理前的处理：\r\n" +
                    "#\t 设置最后一次请求的时间：" + lastRequestTimeFlag + "\r\n" +
                    "#\t 服务器本次启动后处理的请求总数 加1：" + requestCountByStartFlag + "\r\n" +
                    "#\t 服务器当天处理请求总数(00:00:00--23:59:59) 加1：" + requestCountByDayFlag + "\r\n" +
                    "#\t 统计服务器当前小时处理请求总数(n:00:00-n:59:59) 加1：" + requestCountByHourFlag + "\r\n" +
                    "#\t ------------------------------------------------\r\n" +
                    "#\t 最后一次请求的时间：" + DateTimeUtils.getDate(requestStatistics.getLastRequestTime(request, response), "yyyy-MM-dd HH:mm:ss.SSS") + "\r\n" +
                    "#\t 服务器本次启动后处理的请求总数：" + requestStatistics.getRequestCountByStart(request, response) + "\r\n" +
                    "#\t 服务器当天处理请求总数(00:00:00--23:59:59)：" + requestStatistics.getRequestCountByDay(request, response) + "\r\n" +
                    "#\t 服务器当前小时处理请求总数(n:00:00-n:59:59)：" + requestStatistics.getRequestCountByHour(request, response) + "\r\n" +
                    "#=======================================================================================================================#\r\n";
            logger.debug(tmp);
        }
        return true;
    }

    /**
     * 请求处理完成，视图渲染前的回调方法<br/>
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        String viewName = modelAndView == null ? "null" : modelAndView.getViewName();
        logger.debug("### StatisticsInterceptor.postHandle 对应视图名称:" + viewName);
    }

    /**
     * 请求处理完成，视图渲染完成后的回调方法<br/>
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 存在异常
        if (ex != null) {
            logger.error("### 服务器发生异常(afterCompletion)", ex);
        }

        // 从request Attribute中获取异常信息
        Object object = request.getAttribute(CommonRequestAttributes.SERVER_EXCEPTION);
        if (object instanceof Throwable) {
            Throwable throwable = (Throwable) object;
            logger.error("### 服务器发生异常", throwable);
        }

        // 存储请求信息
        if (null != requestStatistics) {
            RequestInfo requestInfo = new RequestInfo();
            requestInfo.setLoginName(getUserUtils().getUserCode());
            requestInfo.setRequestTime(new Date());
            requestInfo.setRequestUri(HttpServletRequestUtils.getRequestURL(request));
            requestInfo.setMethod(request.getMethod());
            requestInfo.setParams(HttpServletRequestUtils.getRequestParams(request));
            requestInfo.setProcessTime(System.currentTimeMillis() - requestStatistics.getRequestStartTime(request, response));
            requestInfo.setRemoteAddr(request.getRemoteAddr() + ":" + request.getRemotePort());
            requestInfo.setUserAgent(UserAgentUtils.getUserAgent(request).toString());
            if (object instanceof Throwable) {
                requestInfo.setHasException('1');
                requestInfo.setExceptionInfo(ExceptionUtils.getStackTraceAsString((Throwable) object));
            } else {
                requestInfo.setHasException('0');
            }
            boolean saveRequestInfoFlag = requestStatistics.saveRequestInfo(request, response, requestInfo);
            if (logger.isDebugEnabled()) {
                String tmp = "\r\n" +
                        "#=======================================================================================================================#\r\n" +
                        "# 请求处理完成(已渲染视图)的处理：\r\n" +
                        "#\t 存储请求信息：" + saveRequestInfoFlag + "\r\n" +
                        "#\t ------------------------------------------------\r\n" +
                        "#\t 登录名：" + requestInfo.getLoginName() + " \r\n" +
                        "#\t 请求时间：" + DateTimeUtils.formatToString(requestInfo.getRequestTime(), "yyyy-MM-dd HH:mm:ss.SSS") + " \r\n" +
                        "#\t 请求URI：" + requestInfo.getRequestUri() + " \r\n" +
                        "#\t 操作方式：" + requestInfo.getMethod() + " \r\n" +
                        "#\t 请求参数数据：" + requestInfo.getParams() + " \r\n" +
                        "#\t 请求处理时间(ms)：" + requestInfo.getProcessTime() + "ms \r\n" +
                        "#\t 客户端的IP地址：" + requestInfo.getRemoteAddr() + " \r\n" +
                        "#\t 用户代理：" + requestInfo.getUserAgent() + " \r\n" +
                        "#\t 是否有异常(0：否；1：是)：" + requestInfo.getHasException() + " \r\n" +
                        "#=======================================================================================================================#\r\n";
                logger.debug(tmp);
            }
        } else {
            logger.error("### StatisticsInterceptor.afterCompletion 服务所有的请求统计实现类未注入，请注入：StatisticsInterceptor.requestStatistics");
        }
    }
}
