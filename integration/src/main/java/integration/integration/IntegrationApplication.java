package integration.integration;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

@SpringBootApplication
@ImportRuntimeHints(IntegrationApplication.Hints.class)
public class IntegrationApplication {

    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(DirectChannel.class, MemberCategory.values());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }

    @Bean
    static MyTaskBFPP myTaskBFPP() {
        return new MyTaskBFPP();
    }

    static class MyTaskBFPP implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // noop
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            if (registry instanceof ConfigurableListableBeanFactory beanFactory) {
                for (var beanDefinitionName : beanFactory.getBeanNamesForAnnotation(SingTask.class)) {
                    var bd = beanFactory.getBeanDefinition(beanDefinitionName);
                    System.out.println(beanDefinitionName + ':' + bd.getBeanClassName());
                    var mcbd = BeanDefinitionBuilder.rootBeanDefinition(DirectChannel.class).getBeanDefinition();
                    var newBeanName = beanDefinitionName + "MessageChannel";
                    if (!registry.containsBeanDefinition(newBeanName))
                        registry.registerBeanDefinition(newBeanName, mcbd);
                }
            }
        }
    }

    @Bean
    ApplicationRunner runner(MyTaskHandler handler) {
        return a -> handler.handle();
    }

    @Bean
    ApplicationRunner applicationRunner(MessageChannel myTaskHandlerMessageChannel) {
        return args -> System.out.println("got a new message channel for christmas " + myTaskHandlerMessageChannel);
    }

    @Bean
    static MyPersistentSingTaskPostProcessor myPersistentSingTaskPostProcessor() {
        return new MyPersistentSingTaskPostProcessor();
    }
}

class MyPersistentSingTaskPostProcessor implements SmartInstantiationAwareBeanPostProcessor {

    private static ProxyFactory buildProxyFactoryFor(Class<?> clazz, Object bean, String beanName) {
        var pfb = new ProxyFactory();

        for (var i : clazz.getInterfaces())
            pfb.addInterface(i);

        pfb.addAdvice((MethodInterceptor) invocation -> {
            if (invocation.getMethod().getName().equals("toString"))
                return "proxied task Handler";

            // write out execution

            System.out.println("starting executing " + beanName + " @ " + System.currentTimeMillis());
            var result = invocation.getMethod().invoke(bean, invocation.getArguments());
            // write out confirmation of execution
            System.out.println("finished executing " + beanName + " @ " + System.currentTimeMillis());
            return result;
        });

        if (null != bean)
            pfb.setTarget(bean);

        pfb.setProxyTargetClass(true);
        pfb.setTargetClass(clazz);
        return pfb;
    }

    private static boolean matches(Class<?> clazz) {
        return (clazz != null && clazz.getAnnotation(SingTask.class) != null);
    }

    @Override
    public Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {
        return matches(beanClass) ?
                buildProxyFactoryFor(beanClass, null, beanName)
                        .getProxyClass(beanClass.getClassLoader()) :
                beanClass;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (matches(bean.getClass())) {
            return buildProxyFactoryFor(bean.getClass(), bean, beanName).getProxy();
        }
        return bean;
    }
}


@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
@Component
@interface SingTask {
    String value() default "";
}

@SingTask
class MyTaskHandler {

    void handle() {
        System.out.println("handled!");
    }
}
