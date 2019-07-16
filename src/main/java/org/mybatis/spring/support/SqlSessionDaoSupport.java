/**
 * Copyright 2010-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.support;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.support.DaoSupport;

/**
 * Convenient super class for MyBatis SqlSession data access objects.
 * It gives you access to the template which can then be used to execute SQL methods.
 * <p>
 * This class needs a SqlSessionTemplate or a SqlSessionFactory.
 * If both are set the SqlSessionFactory will be ignored.
 * <p>
 *
 * @author Putthiphong Boonphong
 * @author Eduardo Macarron
 *
 * @see #setSqlSessionFactory
 * @see #setSqlSessionTemplate
 * @see SqlSessionTemplate
 */
public abstract class SqlSessionDaoSupport extends DaoSupport {

    /**
     * 初始化是通过 {@link #createSqlSessionTemplate} 方法
     */
    private SqlSessionTemplate sqlSessionTemplate;

    /**
     * Set MyBatis SqlSessionFactory to be used by this DAO.
     * Will automatically create SqlSessionTemplate for the given SqlSessionFactory.
     *
     * @param sqlSessionFactory a factory of SqlSession
     *
     * 测试下来发现，这个方法是在创建 {@link org.mybatis.spring.mapper.MapperFactoryBean} 的时候被调用，而且是通过反射
     * 现在疑惑是 spring 为什么会主动设置这个值，虽然 sqlSessionTemplate 是有对应的 get/set 方法，但触发点是什么目前不得而知?
     * 唯一能解释的就是对 mapper bean 定义采用 MapperFactoryBean，逻辑就是在 {@link org.mybatis.spring.mapper.ClassPathMapperScanner#doScan(String...)}
     * 因为设置了 definition.setAutowireMode 所以按照类型注入，spring 就会自动查找所有 setXxx 方法，然后根据参数看看有没有这个 bean，有的话就注入，没有跳过
     * 所以spring通过反射注入 setSqlSessionFactory 所需要的 SqlSessionFactory，同时也就创建了 sqlSessionTemplate
     */
    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        if (this.sqlSessionTemplate == null || sqlSessionFactory != this.sqlSessionTemplate.getSqlSessionFactory()) {
            this.sqlSessionTemplate = createSqlSessionTemplate(sqlSessionFactory);
        }
    }

    /**
     * Create a SqlSessionTemplate for the given SqlSessionFactory.
     * Only invoked if populating the DAO with a SqlSessionFactory reference!
     * <p>Can be overridden in subclasses to provide a SqlSessionTemplate instance
     * with different configuration, or a custom SqlSessionTemplate subclass.
     * @param sqlSessionFactory the MyBatis SqlSessionFactory to create a SqlSessionTemplate for
     * @return the new SqlSessionTemplate instance
     * @see #setSqlSessionFactory
     */
    @SuppressWarnings("WeakerAccess")
    protected SqlSessionTemplate createSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * Return the MyBatis SqlSessionFactory used by this DAO.
     *
     * @return a factory of SqlSession
     */
    public final SqlSessionFactory getSqlSessionFactory() {
        return (this.sqlSessionTemplate != null ? this.sqlSessionTemplate.getSqlSessionFactory() : null);
    }


    /**
     * Set the SqlSessionTemplate for this DAO explicitly,
     * as an alternative to specifying a SqlSessionFactory.
     *
     * @param sqlSessionTemplate a template of SqlSession
     * @see #setSqlSessionFactory
     */
    public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    /**
     * Users should use this method to get a SqlSession to call its statement methods
     * This is SqlSession is managed by spring. Users should not commit/rollback/close it
     * because it will be automatically done.
     *
     * @return Spring managed thread safe SqlSession
     */
    public SqlSession getSqlSession() {
        return this.sqlSessionTemplate;
    }

    /**
     * Return the SqlSessionTemplate for this DAO,
     * pre-initialized with the SessionFactory or set explicitly.
     * <p><b>Note: The returned SqlSessionTemplate is a shared instance.</b>
     * You may introspect its configuration, but not modify the configuration
     * (other than from within an {@link #initDao} implementation).
     * Consider creating a custom SqlSessionTemplate instance via
     * {@code new SqlSessionTemplate(getSqlSessionFactory())}, in which case
     * you're allowed to customize the settings on the resulting instance.
     *
     * @return a template of SqlSession
     */
    public SqlSessionTemplate getSqlSessionTemplate() {
        return this.sqlSessionTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkDaoConfig() {
        notNull(this.sqlSessionTemplate, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
    }

}
