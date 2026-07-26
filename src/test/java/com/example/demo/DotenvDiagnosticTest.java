package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

class DotenvDiagnosticTest {

    @Test
    void isolateDotenvListenerEffect() throws Exception {
        SpringApplication app = new SpringApplication(DemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        Method getOrCreateEnvironment = SpringApplication.class.getDeclaredMethod("getOrCreateEnvironment");
        getOrCreateEnvironment.setAccessible(true);
        ConfigurableEnvironment environment = (ConfigurableEnvironment) getOrCreateEnvironment.invoke(app);

        Method configureEnvironment = SpringApplication.class.getDeclaredMethod(
                "configureEnvironment", ConfigurableEnvironment.class, String[].class);
        configureEnvironment.setAccessible(true);
        configureEnvironment.invoke(app, environment, (Object) new String[0]);

        System.out.println("DOTENV_CHECK env class=" + environment.getClass());
        System.out.println("DOTENV_CHECK sources before dotenv listener=" + environment.getPropertySources());

        Method getRunListeners = SpringApplication.class.getDeclaredMethod("getRunListeners", String[].class);
        getRunListeners.setAccessible(true);
        Object runListeners = getRunListeners.invoke(app, (Object) new String[0]);
        Field listenersField = runListeners.getClass().getDeclaredField("listeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<SpringApplicationRunListener> listeners = (List<SpringApplicationRunListener>) listenersField.get(runListeners);

        SpringApplicationRunListener dotenvListener = listeners.get(0);
        System.out.println("DOTENV_CHECK invoking=" + dotenvListener.getClass().getName());

        System.out.println("DOTENV_CHECK sources.size before=" + environment.getPropertySources().size());

        DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
        try {
            dotenvListener.environmentPrepared(bootstrapContext, environment);
        } catch (Throwable t) {
            System.out.println("DOTENV_CHECK EXCEPTION=" + t);
            t.printStackTrace();
        }

        System.out.println("DOTENV_CHECK sources after dotenv listener=" + environment.getPropertySources());
        System.out.println("DOTENV_CHECK DB_URL_PRESENT=" + environment.containsProperty("DB_URL"));

        java.util.Map<String, Object> probeMap = new java.util.HashMap<>();
        probeMap.put("PROBE_KEY", "1");
        environment.getPropertySources().addAfter("systemEnvironment",
                new org.springframework.core.env.MapPropertySource("myProbe", probeMap));
        System.out.println("DOTENV_CHECK sources after manual addAfter=" + environment.getPropertySources());
        System.out.println("DOTENV_CHECK probe present=" + environment.containsProperty("PROBE_KEY"));

        Class<?> configPropsClass = Class.forName("me.paulschwarz.springdotenv.DotenvConfigProperties");
        Method loadProperties = configPropsClass.getDeclaredMethod("loadProperties");
        loadProperties.setAccessible(true);
        Object props = loadProperties.invoke(null);
        System.out.println("DOTENV_CHECK loaded .env.properties classpath resource=" + props);

        Class<?> configClass = Class.forName("me.paulschwarz.springdotenv.DotenvConfig");
        Object config = configClass.getDeclaredConstructor(java.util.Properties.class).newInstance(props);
        System.out.println("DOTENV_CHECK resolved DotenvConfig=" + config);

        System.out.println("DOTENV_CHECK dotenv classloader=" + dotenvListener.getClass().getClassLoader());
        System.out.println("DOTENV_CHECK env classloader=" + environment.getClass().getClassLoader());
        System.out.println("DOTENV_CHECK MutablePropertySources classloader=" + environment.getPropertySources().getClass().getClassLoader());
    }
}
