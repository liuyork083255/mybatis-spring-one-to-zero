/**
 * Copyright 2010-2019 the original author or authors.
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
package org.mybatis.spring.mapper;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

/**
 * A {@link ClassPathBeanDefinitionScanner} that registers Mappers by
 * {@code basePackage}, {@code annotationClass}, or {@code markerInterface}. If
 * an {@code annotationClass} and/or {@code markerInterface} is specified, only
 * the specified types will be searched (searching for all interfaces will be
 * disabled).
 * <p>
 * This functionality was previously a private class of
 * {@link MapperScannerConfigurer}, but was broken out in version 1.2.0.
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 * @see MapperFactoryBean
 * @since 1.2.0
 *
 * one-to-zero:
 *  mapper 接口映射器的扫描类
 *  mapper 映射器的发现有三种：http://www.mybatis.org/spring/zh/mappers.html
 *      · 使用 <mybatis:scan/> 元素
 *      · 使用 @MapperScan 注解
 *      · 在经典 Spring XML 配置文件中注册一个 MapperScannerConfigurer
 *
 *  但是不管是哪一种，它们都将会使用到 ClassPathMapperScanner 类的解析
 *  前面两种方式都是利用 {@link org.mybatis.spring.annotation.MapperScannerRegistrar}，
 *  第三种使用 {@link MapperScannerConfigurer}
 *
 *  ClassPathMapperScanner 核心工作就是解析指定 包路径 下的所有接口 mapper，然后将这些 mapper 全部解析成 BeanDefinitionHolder
 *  这个时候 BeanDefinitionHolder 并不是一个 bean，而是一些 bean 的定义，spring 容器会自动根据 BeanDefinitionHolder 来创建 bean
 *  这里需要注意：
 *      mapper 接口并没有实现类，而是一个接口，那么spring如何注入？注入的真实 bean 是什么呢？
 *      其实 spring 在获取到mapper定义后，会为 mapper 定义设置一个 beanClass {@link #processBeanDefinitions}，
 *      这个 beanClass 实现了 {@link org.springframework.beans.factory.FactoryBean}，所以在实例化 mapper 接口 bean 的时候
 *      其实是调用 {@link FactoryBean#getObject()}
 *
 */
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathMapperScanner.class);

    private boolean addToConfig = true;

    private SqlSessionFactory sqlSessionFactory;

    private SqlSessionTemplate sqlSessionTemplate;

    private String sqlSessionTemplateBeanName;

    private String sqlSessionFactoryBeanName;

    private Class<? extends Annotation> annotationClass;

    private Class<?> markerInterface;

    private Class<? extends MapperFactoryBean> mapperFactoryBeanClass = MapperFactoryBean.class;

    public ClassPathMapperScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
    }

    public void setAddToConfig(boolean addToConfig) {
        this.addToConfig = addToConfig;
    }

    public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
        this.annotationClass = annotationClass;
    }

    public void setMarkerInterface(Class<?> markerInterface) {
        this.markerInterface = markerInterface;
    }

    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
        this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
    }

    public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
        this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
    }

    /**
     * @deprecated Since 2.0.1, Please use the {@link #setMapperFactoryBeanClass(Class)}.
     */
    @Deprecated
    public void setMapperFactoryBean(MapperFactoryBean<?> mapperFactoryBean) {
        this.mapperFactoryBeanClass = mapperFactoryBean == null ? MapperFactoryBean.class : mapperFactoryBean.getClass();
    }

    /**
     * Set the {@code MapperFactoryBean} class.
     *
     * @param mapperFactoryBeanClass the {@code MapperFactoryBean} class
     * @since 2.0.1
     */
    public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> mapperFactoryBeanClass) {
        this.mapperFactoryBeanClass = mapperFactoryBeanClass == null ? MapperFactoryBean.class : mapperFactoryBeanClass;
    }

    /**
     * Configures parent scanner to search for the right interfaces. It can search
     * for all interfaces or just for those that extends a markerInterface or/and
     * those annotated with the annotationClass
     */
    public void registerFilters() {
        boolean acceptAllInterfaces = true;

        // if specified, use the given annotation and / or marker interface
        if (this.annotationClass != null) {
            addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
            acceptAllInterfaces = false;
        }

        // override AssignableTypeFilter to ignore matches on the actual marker interface
        if (this.markerInterface != null) {
            addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
                @Override
                protected boolean matchClassName(String className) {
                    return false;
                }
            });
            acceptAllInterfaces = false;
        }

        if (acceptAllInterfaces) {
            // default include filter that accepts all classes
            addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
        }

        // exclude package-info.java
        addExcludeFilter((metadataReader, metadataReaderFactory) -> {
            String className = metadataReader.getClassMetadata().getClassName();
            return className.endsWith("package-info");
        });
    }

    /**
     * Calls the parent search that will search and register all the candidates.
     * Then the registered objects are post processed to set them as
     * MapperFactoryBeans
     *
     * 这里的工作就是将指定包下的接口获取 bean 的定义
     * 然后对这些定义做 后置处理，将 beanClass 设置为 MapperFactoryBean
     */
    @Override
    public Set<BeanDefinitionHolder> doScan(String... basePackages) {
        /*
        * 调用父方法搜索，它将搜索并注册所有候选项。
        * 比如 basePackages 下面有两个 mapper 接口，这里就会返回两个 beanDefinition
        */
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

        if (beanDefinitions.isEmpty()) {
            LOGGER.warn(() -> "No MyBatis mapper was found in '" + Arrays.toString(basePackages) + "' package. Please check your configuration.");
        } else {
            /* 对已注册的对象进行后处理，将它们设置为 mapperFactoryBean  */
            processBeanDefinitions(beanDefinitions);
        }

        return beanDefinitions;
    }

    /**
     * 这里的 mapper 接口bean 定义，仅仅是接口相关的信息，并没有详细的属性值
     * 所以这个方法就是定义它的属性和具体的实现类
     * 具体的实现类就是 {@link MapperFactoryBean}，这个类里面有属性
     *  {@link MapperFactoryBean#addToConfig} 和
     *  {@link MapperFactoryBean#sqlSessionTemplate}
     *  spring 在创建这个 bean 的时候会自动调用 {@link MapperFactoryBean#setSqlSessionFactory(SqlSessionFactory)}
     *  现在也不清楚为什么会自动调用这个方法，难道是方法末尾的 definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE)?
     *  debug 发现每一个 mapper 都有一个属性 sqlSessionFactory，所以会调用对应的 set 方法，但是debug也同时发现，这个方法里面
     *  默认情况下并没有执行添加属性 sqlSessionFactory 代码，不知道是怎么添加的
     */
    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
        GenericBeanDefinition definition;
        for (BeanDefinitionHolder holder : beanDefinitions) {
            /* 可以理解这里的 holder 就是一个 mapper 接口定义 */

            definition = (GenericBeanDefinition) holder.getBeanDefinition();

            /* 获取 mapper 接口的全类路径 */
            String beanClassName = definition.getBeanClassName();

            // the mapper interface is the original class of the bean
            // but, the actual class of the bean is MapperFactoryBean

            /* 这里很重要，mapper 接口只是一个接口，并没有实现类，所以 bean 的实际类是 MapperFactoryBean，这里是指定 mapper 接口对应的 bean-name */
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName); // issue #59

            /**
             * 设置 beanClass 为 {@link MapperFactoryBean}，这个 MapperFactoryBean 尤为重要
             * 当 spring 注入这个 definition 的时候，实际上调用的是该 MapperFactoryBean 的 getObject() 方法来获得特定的 mapper 实例
             * 这个 mapper 接口具体的实现类是由 beanClassName 来设置的。
             * 这么设置以后，我们调用
             *  @Autowired
             *  private UserMapper userMapper;
             *  其实就是调用的 MapperFactoryBean 中的 getObject() 获取特定的mapper实例
             *  测试下来确实如此，如果注入 mapper 类，那么就不会调用 MapperFactoryBean 中的 getObject() 方法
             *
             * mapper 接口的具体实现类还是利用 mybatis 的jdk 动态代理，创建机制就是 MapperFactoryBean
             */
            definition.setBeanClass(this.mapperFactoryBeanClass);

            definition.getPropertyValues().add("addToConfig", this.addToConfig);

            /**
             * 调试下来，默认这里没有主动指定，是不会添加属性的，不执行
             */
            boolean explicitFactoryUsed = false;
            if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
                definition.getPropertyValues().add("sqlSessionFactory", new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
                explicitFactoryUsed = true;
            } else if (this.sqlSessionFactory != null) {
                definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
                explicitFactoryUsed = true;
            }

            /**
             * 调试下来，默认这里没有主动指定，是不会添加属性的，不执行
             */
            if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
                if (explicitFactoryUsed) {
                    LOGGER.warn(() -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
                }
                definition.getPropertyValues().add("sqlSessionTemplate", new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
                explicitFactoryUsed = true;
            } else if (this.sqlSessionTemplate != null) {
                if (explicitFactoryUsed) {
                    LOGGER.warn(() -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
                }
                definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
                explicitFactoryUsed = true;
            }

            if (!explicitFactoryUsed) {
                LOGGER.debug(() -> "Enabling autowire by type for MapperFactoryBean with name '" + holder.getBeanName() + "'.");
                definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
        if (super.checkCandidate(beanName, beanDefinition)) {
            return true;
        } else {
            LOGGER.warn(() -> "Skipping MapperFactoryBean with name '" + beanName
                    + "' and '" + beanDefinition.getBeanClassName() + "' mapperInterface"
                    + ". Bean already defined with the same name!");
            return false;
        }
    }

}
