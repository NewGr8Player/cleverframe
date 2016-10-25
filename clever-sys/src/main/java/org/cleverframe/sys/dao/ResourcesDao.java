package org.cleverframe.sys.dao;

import org.cleverframe.common.persistence.Page;
import org.cleverframe.common.persistence.Parameter;
import org.cleverframe.core.persistence.dao.BaseDao;
import org.cleverframe.core.utils.QLScriptUtils;
import org.cleverframe.sys.SysBeanNames;
import org.cleverframe.sys.entity.Resources;
import org.hibernate.SQLQuery;
import org.hibernate.transform.Transformers;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 作者：LiZW <br/>
 * 创建时间：2016/10/21 17:08 <br/>
 */
@Repository(SysBeanNames.ResourcesDao)
public class ResourcesDao extends BaseDao<Resources> {

    /**
     * 分页查询数据
     *
     * @param title         资源标题
     * @param resourcesUrl  资源URL地址
     * @param permission    资源访问所需要的权限标识字符串
     * @param resourcesType 资源类型（1：URL资源；2：UI资源）
     */
    public Page<Resources> findByPage(Page<Resources> page, String title, String resourcesUrl, String permission, String resourcesType) {
        Parameter param = new Parameter();
        param.put("title", "%" + title + "%");
        param.put("resourcesUrl", "%" + resourcesUrl + "%");
        param.put("permission", permission);
        param.put("resourcesType", resourcesType);
        String sql = QLScriptUtils.getSQLScript("org.cleverframe.sys.dao.ResourcesDao.findByPage");
        return hibernateDao.findBySql(page, sql, param);
    }

    /**
     * 查询一个页面资源的所有依赖资源
     *
     * @param resourcesId 页面资源ID
     */
    public List<Resources> findDependenceResources(Serializable resourcesId) {
        Parameter param = new Parameter();
        param.put("resourcesId", resourcesId);
        String sql = QLScriptUtils.getSQLScript("org.cleverframe.sys.dao.ResourcesDao.findDependenceResources");
        return hibernateDao.findBySql(sql, param);
    }

    /**
     * 为页面资源增加一个依赖资源
     *
     * @param resourcesId           资源ID
     * @param dependenceResourcesId 依赖的资源ID
     * @return 成功返回true
     */
    public boolean addDependenceResources(Serializable resourcesId, Serializable dependenceResourcesId) {
        Parameter param = new Parameter();
        param.put("resourcesId", resourcesId);
        param.put("dependenceResourcesId", dependenceResourcesId);
        String sql = QLScriptUtils.getSQLScript("org.cleverframe.sys.dao.ResourcesDao.addDependenceResources");
        SQLQuery sqlQuery = hibernateDao.createSqlQuery(sql, param);
        return sqlQuery.executeUpdate() >= 1;
    }

    /**
     * 为页面资源删除一个依赖资源
     *
     * @param resourcesId           资源ID
     * @param dependenceResourcesId 依赖的资源ID
     * @return 成功返回true
     */
    public boolean deleteDependenceResources(Serializable resourcesId, Serializable dependenceResourcesId) {
        Parameter param = new Parameter();
        param.put("resourcesId", resourcesId);
        param.put("dependenceResourcesId", dependenceResourcesId);
        String sql = QLScriptUtils.getSQLScript("org.cleverframe.sys.dao.ResourcesDao.deleteDependenceResources");
        SQLQuery sqlQuery = hibernateDao.createSqlQuery(sql, param);
        return sqlQuery.executeUpdate() >= 1;
    }

    /**
     * 查询所有的资源信息数据
     */
    public List<Resources> findAllResources() {
        String sql = QLScriptUtils.getSQLScript("org.cleverframe.sys.dao.ResourcesDao.findAllResources");
        return hibernateDao.findBySql(sql);
    }

    /**
     * 查询所有的资源关系信息
     */
    public List<Map<String, Object>> findAllResourcesRelation() {
        String sql = QLScriptUtils.getSQLScript("org.cleverframe.sys.dao.ResourcesDao.findAllResourcesRelation");
        SQLQuery sqlQuery = hibernateDao.createSqlQuery(sql, null);
        sqlQuery.setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
        //noinspection unchecked,UnnecessaryLocalVariable
        List<Map<String, Object>> list = sqlQuery.list();
        return list;
    }
}