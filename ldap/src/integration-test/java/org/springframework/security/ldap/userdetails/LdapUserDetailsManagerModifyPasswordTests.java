/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.ldap.userdetails;

import javax.annotation.PreDestroy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.ContextSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.ldap.DefaultLdapUsernameToDnMapper;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.security.ldap.server.UnboundIdContainer;
import org.springframework.security.test.context.annotation.SecurityTestExecutionListeners;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link LdapUserDetailsManager#changePassword}, specifically relating to the
 * use of the Modify Password Extended Operation.
 *
 * @author Josh Cummings
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SecurityTestExecutionListeners
public class LdapUserDetailsManagerModifyPasswordTests {

	ConfigurableApplicationContext context;

	LdapUserDetailsManager userDetailsManager;
	ContextSource contextSource;

	@Before
	public void setup() {
		this.context = new AnnotationConfigApplicationContext(ContainerConfiguration.class, LdapConfiguration.class);
		this.contextSource = this.context.getBean(ContextSource.class);

		this.userDetailsManager = new LdapUserDetailsManager(this.contextSource);
		this.userDetailsManager.setUsePasswordModifyExtensionOperation(true);
		this.userDetailsManager.setUsernameMapper(new DefaultLdapUsernameToDnMapper("ou=people", "uid"));
	}

	@After
	public void teardown() {
		this.context.close();
	}

	@Test
	@WithMockUser(username="bob", password="bobspassword", authorities="ROLE_USER")
	public void changePasswordWhenOldPasswordIsIncorrectThenThrowsException() {
		assertThatCode(() ->
				this.userDetailsManager.changePassword("wrongoldpassword", "bobsnewpassword"))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@WithMockUser(username="bob", password="bobspassword", authorities="ROLE_USER")
	public void changePasswordWhenOldPasswordIsCorrectThenPasses() {
		SpringSecurityLdapTemplate template = new SpringSecurityLdapTemplate(this.contextSource);

		this.userDetailsManager.changePassword("bobspassword",
				"bobsshinynewandformidablylongandnearlyimpossibletorememberthoughdemonstrablyhardtocrackduetoitshighlevelofentropypasswordofjustice");

		assertThat(template.compare("uid=bob,ou=people", "userPassword",
				"bobsshinynewandformidablylongandnearlyimpossibletorememberthoughdemonstrablyhardtocrackduetoitshighlevelofentropypasswordofjustice")).isTrue();
	}

	@Configuration
	static class LdapConfiguration {
		@Autowired UnboundIdContainer container;

		@Bean
		ContextSource contextSource() throws Exception {
			return new DefaultSpringSecurityContextSource("ldap://127.0.0.1:"
					+ this.container.getPort() + "/dc=springframework,dc=org");
		}
	}

	@Configuration
	static class ContainerConfiguration {
		UnboundIdContainer container = new UnboundIdContainer("dc=springframework,dc=org",
				"classpath:test-server.ldif");

		@Bean
		UnboundIdContainer ldapContainer() {
			this.container.setPort(0);
			return this.container;
		}

		@PreDestroy
		void shutdown() {
			this.container.stop();
		}
	}
}
