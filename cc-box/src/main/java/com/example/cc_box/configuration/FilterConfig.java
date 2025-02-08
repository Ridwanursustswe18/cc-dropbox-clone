package com.example.cc_box.configuration;

import com.example.cc_box.share_link.SharedLinkFilter;
import com.example.cc_box.share_link.SharedLinkService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public SharedLinkFilter sharedLinkFilter(SharedLinkService sharedLinkService) {
        return new SharedLinkFilter(sharedLinkService);
    }

    @Bean
    public FilterRegistrationBean<SharedLinkFilter> sharedLinkFilterRegistration(SharedLinkFilter sharedLinkFilter) {
        FilterRegistrationBean<SharedLinkFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(sharedLinkFilter);
        registrationBean.addUrlPatterns("/share/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}