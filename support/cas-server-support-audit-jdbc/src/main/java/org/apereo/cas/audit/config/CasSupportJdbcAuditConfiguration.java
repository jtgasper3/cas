package org.apereo.cas.audit.config;

import org.apereo.cas.audit.AuditTrailExecutionPlanConfigurer;
import org.apereo.cas.audit.spi.entity.AuditTrailEntity;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.core.audit.AuditJdbcProperties;
import org.apereo.cas.configuration.model.support.jpa.JpaConfigurationContext;
import org.apereo.cas.configuration.support.JpaBeans;
import org.apereo.cas.jpa.JpaBeanFactory;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.spring.beans.BeanCondition;
import org.apereo.cas.util.spring.beans.BeanSupplier;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apereo.inspektr.audit.AuditTrailManager;
import org.apereo.inspektr.audit.support.JdbcAuditTrailManager;
import org.apereo.inspektr.audit.support.MaxAgeWhereClauseMatchCriteria;
import org.apereo.inspektr.audit.support.WhereClauseMatchCriteria;
import org.apereo.inspektr.common.Cleanable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

/**
 * This is {@link CasSupportJdbcAuditConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration(value = "CasJdbcAuditConfiguration", proxyBeanMethods = false)
@EnableAspectJAutoProxy
@EnableConfigurationProperties(CasConfigurationProperties.class)
@EnableTransactionManagement
public class CasSupportJdbcAuditConfiguration {

    @Configuration(value = "CasSupportJdbcAuditEntityConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditEntityConfiguration {

        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public EntityManagerFactory inspektrAuditEntityManagerFactory(
            @Qualifier("inspektrAuditTrailDataSource")
            final DataSource inspektrAuditTrailDataSource, final CasConfigurationProperties casProperties,
            @Qualifier(JpaBeanFactory.DEFAULT_BEAN_NAME)
            final JpaBeanFactory jpaBeanFactory) throws Exception {
            val ctx = JpaConfigurationContext.builder()
                .jpaVendorAdapter(jpaBeanFactory.newJpaVendorAdapter(casProperties.getJdbc()))
                .persistenceUnitName("jpaInspektrAuditContext")
                .dataSource(inspektrAuditTrailDataSource)
                .packagesToScan(CollectionUtils.wrapSet(AuditTrailEntity.class.getPackage().getName()))
                .build();
            return jpaBeanFactory.newEntityManagerFactoryBean(ctx, casProperties.getAudit().getJdbc()).getObject();
        }

        @ConditionalOnMissingBean(name = "auditCleanupCriteria")
        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public WhereClauseMatchCriteria auditCleanupCriteria(final CasConfigurationProperties casProperties) {
            return new MaxAgeWhereClauseMatchCriteria(casProperties.getAudit().getJdbc().getMaxAgeDays());
        }
    }

    @Configuration(value = "CasSupportJdbcAuditTransactionTemplateConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditTransactionTemplateConfiguration {

        @ConditionalOnMissingBean(name = "inspektrAuditTransactionTemplate")
        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public TransactionOperations inspektrAuditTransactionTemplate(
            @Qualifier("inspektrAuditTransactionManager")
            final PlatformTransactionManager inspektrAuditTransactionManager, final CasConfigurationProperties casProperties) {
            val t = new TransactionTemplate(inspektrAuditTransactionManager);
            val jdbc = casProperties.getAudit().getJdbc();
            t.setIsolationLevelName(jdbc.getIsolationLevelName());
            t.setPropagationBehaviorName(jdbc.getPropagationBehaviorName());
            return t;
        }
    }

    @Configuration(value = "CasSupportJdbcAuditManagerConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditManagerConfiguration {
        private static String getAuditTableNameFrom(final AuditJdbcProperties jdbc) {
            var tableName = AuditTrailEntity.AUDIT_TRAIL_TABLE_NAME;
            if (StringUtils.isNotBlank(jdbc.getDefaultSchema())) {
                tableName = jdbc.getDefaultSchema().concat(".").concat(tableName);
            }
            if (StringUtils.isNotBlank(jdbc.getDefaultCatalog())) {
                tableName = jdbc.getDefaultCatalog().concat(".").concat(tableName);
            }
            return tableName;
        }

        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public AuditTrailManager jdbcAuditTrailManager(
            @Qualifier("auditCleanupCriteria")
            final WhereClauseMatchCriteria auditCleanupCriteria,
            @Qualifier("inspektrAuditTransactionTemplate")
            final TransactionOperations inspektrAuditTransactionTemplate,
            @Qualifier("inspektrAuditTrailDataSource")
            final DataSource inspektrAuditTrailDataSource, final CasConfigurationProperties casProperties) {
            val jdbc = casProperties.getAudit().getJdbc();
            val t = new JdbcAuditTrailManager(inspektrAuditTransactionTemplate);
            t.setCleanupCriteria(auditCleanupCriteria);
            t.setDataSource(inspektrAuditTrailDataSource);
            t.setAsynchronous(jdbc.isAsynchronous());
            t.setColumnLength(jdbc.getColumnLength());
            t.setTableName(getAuditTableNameFrom(jdbc));
            if (StringUtils.isNotBlank(jdbc.getSelectSqlQueryTemplate())) {
                t.setSelectByDateSqlTemplate(jdbc.getSelectSqlQueryTemplate());
            }
            if (StringUtils.isNotBlank(jdbc.getDateFormatterPattern())) {
                t.setDateFormatterPattern(jdbc.getDateFormatterPattern());
            }
            return t;
        }
    }

    @Configuration(value = "CasSupportJdbcAuditExecutionPlanConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditExecutionPlanConfiguration {
        @ConditionalOnMissingBean(name = "jdbcAuditTrailExecutionPlanConfigurer")
        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public AuditTrailExecutionPlanConfigurer jdbcAuditTrailExecutionPlanConfigurer(
            @Qualifier("jdbcAuditTrailManager")
            final AuditTrailManager jdbcAuditTrailManager) {
            return plan -> plan.registerAuditTrailManager(jdbcAuditTrailManager);
        }

    }

    @Configuration(value = "CasSupportJdbcAuditScheduleConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditScheduleConfiguration {

        @ConditionalOnMissingBean(name = "inspektrAuditTrailCleaner")
        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public Cleanable inspektrAuditTrailCleaner(
            final ConfigurableApplicationContext applicationContext,
            @Qualifier("jdbcAuditTrailManager")
            final AuditTrailManager jdbcAuditTrailManager) throws Exception {
            return BeanSupplier.of(Cleanable.class)
                .when(BeanCondition.on("cas.audit.jdbc.schedule.enabled").isTrue().evenIfMissing()
                    .given(applicationContext.getEnvironment()))
                .supply(() -> new Cleanable() {
                    @Scheduled(
                        initialDelayString = "${cas.audit.jdbc.schedule.start-delay:10000}",
                        fixedDelayString = "${cas.audit.jdbc.schedule.repeat-interval:30000}")
                    @Override
                    public void clean() {
                        jdbcAuditTrailManager.clean();
                    }
                })
                .otherwiseProxy()
                .get();
        }

    }

    @Configuration(value = "CasSupportJdbcAuditTransactionConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditTransactionConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "inspektrAuditTransactionManager")
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public PlatformTransactionManager inspektrAuditTransactionManager(
            @Qualifier("inspektrAuditTrailDataSource")
            final DataSource inspektrAuditTrailDataSource) {
            return new DataSourceTransactionManager(inspektrAuditTrailDataSource);
        }
    }

    @Configuration(value = "CasSupportJdbcAuditDataConfiguration", proxyBeanMethods = false)
    @EnableConfigurationProperties(CasConfigurationProperties.class)
    public static class CasSupportJdbcAuditDataConfiguration {

        @ConditionalOnMissingBean(name = "inspektrAuditTrailDataSource")
        @Bean
        @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
        public DataSource inspektrAuditTrailDataSource(final CasConfigurationProperties casProperties) {
            return JpaBeans.newDataSource(casProperties.getAudit().getJdbc());
        }

    }
}
