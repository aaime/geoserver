/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.spring;

import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

public class InfrastructureRoleAssigner implements BeanFactoryPostProcessor {
    private final String[] beanNames;
    private final Class<?>[] infrastructureClasses;

    // Constructor for explicit bean names only
    public InfrastructureRoleAssigner(String[] beanNames) {
        this(beanNames, null);
    }

    // Constructor for both bean names and classes
    public InfrastructureRoleAssigner(String[] beanNames, Class<?>[] infrastructureClasses) {
        this.beanNames = beanNames != null ? beanNames : new String[0];
        this.infrastructureClasses = infrastructureClasses != null ? infrastructureClasses : new Class<?>[0];
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Set<String> processedBeans = new HashSet<>();

        // Process explicit bean names
        for (String beanName : beanNames) {
            if (beanFactory.containsBeanDefinition(beanName)) {
                markAsInfrastructure(beanFactory.getBeanDefinition(beanName));
                processedBeans.add(beanName);
            }
        }

        // Process beans by class hierarchy
        if (infrastructureClasses.length > 0) {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                if (processedBeans.contains(beanName)) {
                    continue;
                }

                BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                if (isInfrastructureClass(bd, beanFactory)) {
                    markAsInfrastructure(bd);
                }
            }
        }
    }

    private boolean isInfrastructureClass(BeanDefinition bd, ConfigurableListableBeanFactory beanFactory) {
        String className = bd.getBeanClassName();
        if (className == null) {
            return false;
        }

        try {
            Class<?> beanClass = ClassUtils.forName(className, beanFactory.getBeanClassLoader());

            for (Class<?> infrastructureClass : infrastructureClasses) {
                if (infrastructureClass.isAssignableFrom(beanClass)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            // Ignore beans whose class can't be loaded
        }

        return false;
    }

    private void markAsInfrastructure(BeanDefinition bd) {
        bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    }
}
