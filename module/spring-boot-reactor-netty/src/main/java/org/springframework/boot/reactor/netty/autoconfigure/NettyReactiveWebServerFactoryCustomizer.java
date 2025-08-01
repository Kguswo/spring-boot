/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.reactor.netty.autoconfigure;

import java.time.Duration;

import io.netty.channel.ChannelOption;

import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Customization for Netty-specific features.
 *
 * @author Brian Clozel
 * @author Chentao Qu
 * @author Artsiom Yudovin
 * @since 4.0.0
 */
public class NettyReactiveWebServerFactoryCustomizer
		implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory>, Ordered {

	private final Environment environment;

	private final ServerProperties serverProperties;

	private final NettyServerProperties nettyProperties;

	public NettyReactiveWebServerFactoryCustomizer(Environment environment, ServerProperties serverProperties,
			NettyServerProperties nettyProperties) {
		this.environment = environment;
		this.serverProperties = serverProperties;
		this.nettyProperties = nettyProperties;
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void customize(NettyReactiveWebServerFactory factory) {
		factory.setUseForwardHeaders(getOrDeduceUseForwardHeaders());
		PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
		map.from(this.nettyProperties::getConnectionTimeout)
			.to((connectionTimeout) -> customizeConnectionTimeout(factory, connectionTimeout));
		map.from(this.nettyProperties::getIdleTimeout).to((idleTimeout) -> customizeIdleTimeout(factory, idleTimeout));
		map.from(this.nettyProperties::getMaxKeepAliveRequests)
			.to((maxKeepAliveRequests) -> customizeMaxKeepAliveRequests(factory, maxKeepAliveRequests));
		if (this.serverProperties.getHttp2() != null && this.serverProperties.getHttp2().isEnabled()) {
			map.from(this.serverProperties.getMaxHttpRequestHeaderSize())
				.to((size) -> customizeHttp2MaxHeaderSize(factory, size.toBytes()));
		}
		customizeRequestDecoder(factory, map);
	}

	private boolean getOrDeduceUseForwardHeaders() {
		if (this.serverProperties.getForwardHeadersStrategy() == null) {
			CloudPlatform platform = CloudPlatform.getActive(this.environment);
			return platform != null && platform.isUsingForwardHeaders();
		}
		return this.serverProperties.getForwardHeadersStrategy().equals(ServerProperties.ForwardHeadersStrategy.NATIVE);
	}

	private void customizeConnectionTimeout(NettyReactiveWebServerFactory factory, Duration connectionTimeout) {
		factory.addServerCustomizers((httpServer) -> httpServer.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
				(int) connectionTimeout.toMillis()));
	}

	private void customizeRequestDecoder(NettyReactiveWebServerFactory factory, PropertyMapper propertyMapper) {
		factory.addServerCustomizers((httpServer) -> httpServer.httpRequestDecoder((httpRequestDecoderSpec) -> {
			propertyMapper.from(this.serverProperties.getMaxHttpRequestHeaderSize())
				.to((maxHttpRequestHeader) -> httpRequestDecoderSpec
					.maxHeaderSize((int) maxHttpRequestHeader.toBytes()));
			propertyMapper.from(this.nettyProperties.getMaxInitialLineLength())
				.to((maxInitialLineLength) -> httpRequestDecoderSpec
					.maxInitialLineLength((int) maxInitialLineLength.toBytes()));
			propertyMapper.from(this.nettyProperties.getH2cMaxContentLength())
				.to((h2cMaxContentLength) -> httpRequestDecoderSpec
					.h2cMaxContentLength((int) h2cMaxContentLength.toBytes()));
			propertyMapper.from(this.nettyProperties.getInitialBufferSize())
				.to((initialBufferSize) -> httpRequestDecoderSpec.initialBufferSize((int) initialBufferSize.toBytes()));
			propertyMapper.from(this.nettyProperties.isValidateHeaders()).to(httpRequestDecoderSpec::validateHeaders);
			return httpRequestDecoderSpec;
		}));
	}

	private void customizeIdleTimeout(NettyReactiveWebServerFactory factory, Duration idleTimeout) {
		factory.addServerCustomizers((httpServer) -> httpServer.idleTimeout(idleTimeout));
	}

	private void customizeMaxKeepAliveRequests(NettyReactiveWebServerFactory factory, int maxKeepAliveRequests) {
		factory.addServerCustomizers((httpServer) -> httpServer.maxKeepAliveRequests(maxKeepAliveRequests));
	}

	private void customizeHttp2MaxHeaderSize(NettyReactiveWebServerFactory factory, long size) {
		factory.addServerCustomizers(
				((httpServer) -> httpServer.http2Settings((settings) -> settings.maxHeaderListSize(size))));
	}

}
