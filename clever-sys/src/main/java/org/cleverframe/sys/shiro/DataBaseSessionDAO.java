package org.cleverframe.sys.shiro;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.cleverframe.sys.attributes.SysSessionAttributes;
import org.cleverframe.sys.entity.LoginSession;
import org.cleverframe.sys.entity.User;
import org.cleverframe.sys.service.LoginSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Date;

/**
 * 使用数据库存储Shiro用户登录Session信息,方便计算和查询(分页)在线人数等信息<br/>
 * 参考 EnterpriseCacheSessionDAO<br/>
 * <b>注意：使用数据库存储Session信息性能不高，建议使用Redis缓存</b>
 * 作者：LiZW <br/>
 * 创建时间：2016/11/13 22:54 <br/>
 *
 * @see org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO
 */
public class DataBaseSessionDAO extends CachingSessionDAO {
    /**
     * 日志对象
     */
    private final static Logger logger = LoggerFactory.getLogger(DataBaseSessionDAO.class);

    private LoginSessionService loginSessionService;

    public DataBaseSessionDAO(LoginSessionService loginSessionService) {
        this.loginSessionService = loginSessionService;
    }

    /**
     * 从Shiro Session中获取user对象
     *
     * @param session Shiro Session
     * @return 不存在返回null
     */
    private User getUserBySession(Session session) {
        if (session == null || session.getAttribute(SysSessionAttributes.LOGIN_USER) == null) {
            return null;
        }
        Object object = session.getAttribute(SysSessionAttributes.LOGIN_USER);
        if (!(object instanceof User)) {
            logger.error("Shiro Session中的属性值不能转换成User对象，属性[{}]", SysSessionAttributes.LOGIN_USER);
            return null;
        }
        return (User) object;
    }

    /**
     * 从Shiro Session中获取 用户是否登录通过
     *
     * @param session Shiro Session
     * @return {@link org.cleverframe.core.persistence.entity.BaseEntity#YES} 或者 {@link org.cleverframe.core.persistence.entity.BaseEntity#NO}
     */
    private Character getIsOnLineBySession(Session session) {
        Character result = User.NO;
        if (session == null) {
            return result;
        }
        if (session.getAttribute(DefaultSubjectContext.AUTHENTICATED_SESSION_KEY) != null) {
            if ("true".equalsIgnoreCase(session.getAttribute(DefaultSubjectContext.AUTHENTICATED_SESSION_KEY).toString())) {
                result = User.YES;
            } else if ("false".equalsIgnoreCase(session.getAttribute(DefaultSubjectContext.AUTHENTICATED_SESSION_KEY).toString())) {
                result = User.NO;
            } else {
                logger.error("Shiro Session用户是否登录通过属性值未知，{}={}",
                        DefaultSubjectContext.AUTHENTICATED_SESSION_KEY,
                        session.getAttribute(DefaultSubjectContext.AUTHENTICATED_SESSION_KEY));
            }
        }
        return result;
    }

    /**
     * 根据Shiro Sessio为LoginSession设置值
     *
     * @param session      LoginSession
     * @param loginSession 可以为空，为空就新建一个
     * @return session为空或sessionId为空 返回null
     */
    private LoginSession getLoginSessionBySession(Session session, LoginSession loginSession) {
        if (session == null || session.getId() == null) {
            return null;
        }
        if (loginSession == null) {
            loginSession = new LoginSession();
        }
        User user = getUserBySession(session);
        String sessionId = (String) session.getId();
        loginSession.setSessionId(sessionId);
        loginSession.setLoginName(user == null ? null : user.getLoginName());
        loginSession.setSessionObject(SerializationUtils.serialize((Serializable) session));
        loginSession.setOnLine(getIsOnLineBySession(session));
        loginSession.setHostIp(session.getHost());
        return loginSession;
    }

    /**
     * 调用update时，先调用doUpdate，再验证Session是否失效，失效就从缓存中移除
     */
    @Override
    protected void doUpdate(Session session) {
        String sessionId = (String) session.getId();
        if (StringUtils.isBlank(sessionId)) {
            logger.error("Shiro Session ID 不能为空 - doUpdate");
            return;
        }
        LoginSession loginSession = loginSessionService.getBySessionId(sessionId);
        if (loginSession == null) {
            doCreate(session);
            return;
        }
        loginSession = getLoginSessionBySession(session, loginSession);
        loginSession.setUpdateDate(new Date());
        loginSessionService.update(loginSession);
        logger.debug("Session更新成功, SessionId=[{}]", sessionId);
    }

    /**
     * 调用delete时，先从缓存中移除Session，再调用doDelete
     */
    @Override
    protected void doDelete(Session session) {
        String sessionId = (String) session.getId();
        if (StringUtils.isBlank(sessionId)) {
            logger.error("Shiro Session ID 不能为空 - doDelete");
            return;
        }
        boolean flag = loginSessionService.deleteBySessionId(sessionId);
        if (!flag) {
            RuntimeException exception = new RuntimeException("Shiro Session 删除失败");
            logger.error(exception.getMessage(), exception);
        } else {
            logger.debug("Session删除成功, SessionId=[{}]", sessionId);
        }
    }

    /**
     * 调用create时，先调用doCreate获取sessionId，再缓存Session
     */
    @Override
    protected Serializable doCreate(Session session) {
        Serializable sessionId = this.generateSessionId(session);
        assignSessionId(session, sessionId);
        String strId = (String) session.getId();
        if (StringUtils.isBlank(strId)) {
            throw new RuntimeException("Shiro Session ID 不能为空");
        }
        if (!(session instanceof Serializable)) {
            throw new RuntimeException("Shiro Session没有实现Serializable接口不能序列化存储");
        }
        LoginSession loginSession = getLoginSessionBySession(session, null);
        loginSession.setCreateDate(new Date());
        loginSessionService.save(loginSession);
        logger.debug("Session新增成功, SessionId=[{}]", sessionId);
        return sessionId;
    }

    /**
     * 调用readSession读取Session信息时，先从缓存中查询，缓存中查询不到才调用doReadSession方法
     */
    @Override
    protected Session doReadSession(Serializable sessionId) {
        String strId = (String) sessionId;
        if (StringUtils.isBlank(strId)) {
            throw new RuntimeException("Shiro Session ID 不能为空");
        }
        LoginSession loginSession = loginSessionService.getBySessionId(strId);
        Session session = null;
        if (loginSession != null && loginSession.getSessionObject() != null) {
            session = SerializationUtils.deserialize(loginSession.getSessionObject());
            logger.debug("Session读取成功, SessionId=[{}]", strId);
            cache(session, session.getId());
        }
        return session;
    }
}