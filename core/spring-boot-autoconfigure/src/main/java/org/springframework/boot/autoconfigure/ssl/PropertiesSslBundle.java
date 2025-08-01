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

package org.springframework.boot.autoconfigure.ssl;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.ssl.SslBundleProperties.Key;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.ssl.jks.JksSslStoreBundle;
import org.springframework.boot.ssl.jks.JksSslStoreDetails;
import org.springframework.boot.ssl.pem.PemSslStore;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * {@link SslBundle} backed by {@link JksSslBundleProperties} or
 * {@link PemSslBundleProperties}.
 *
 * @author Scott Frederick
 * @author Phillip Webb
 * @since 3.1.0
 */
public final class PropertiesSslBundle implements SslBundle {

	private final SslStoreBundle stores;

	private final SslBundleKey key;

	private final SslOptions options;

	private final String protocol;

	private final SslManagerBundle managers;

	private PropertiesSslBundle(SslStoreBundle stores, SslBundleProperties properties) {
		this.stores = stores;
		this.key = asSslKeyReference(properties.getKey());
		this.options = asSslOptions(properties.getOptions());
		this.protocol = properties.getProtocol();
		this.managers = SslManagerBundle.from(this.stores, this.key);
	}

	private static SslBundleKey asSslKeyReference(@Nullable Key key) {
		return (key != null) ? SslBundleKey.of(key.getPassword(), key.getAlias()) : SslBundleKey.NONE;
	}

	private static SslOptions asSslOptions(SslBundleProperties.@Nullable Options options) {
		return (options != null) ? SslOptions.of(options.getCiphers(), options.getEnabledProtocols()) : SslOptions.NONE;
	}

	@Override
	public SslStoreBundle getStores() {
		return this.stores;
	}

	@Override
	public SslBundleKey getKey() {
		return this.key;
	}

	@Override
	public SslOptions getOptions() {
		return this.options;
	}

	@Override
	public String getProtocol() {
		return this.protocol;
	}

	@Override
	public SslManagerBundle getManagers() {
		return this.managers;
	}

	/**
	 * Get an {@link SslBundle} for the given {@link PemSslBundleProperties}.
	 * @param properties the source properties
	 * @return an {@link SslBundle} instance
	 */
	public static SslBundle get(PemSslBundleProperties properties) {
		return get(properties, ApplicationResourceLoader.get());
	}

	/**
	 * Get an {@link SslBundle} for the given {@link PemSslBundleProperties}.
	 * @param properties the source properties
	 * @param resourceLoader the resource loader used to load content
	 * @return an {@link SslBundle} instance
	 * @since 3.3.5
	 */
	public static SslBundle get(PemSslBundleProperties properties, ResourceLoader resourceLoader) {
		PemSslStore keyStore = getPemSslStore("keystore", properties.getKeystore(), resourceLoader);
		if (keyStore != null) {
			keyStore = keyStore.withAlias(properties.getKey().getAlias())
				.withPassword(properties.getKey().getPassword());
		}
		PemSslStore trustStore = getPemSslStore("truststore", properties.getTruststore(), resourceLoader);
		SslStoreBundle storeBundle = new PemSslStoreBundle(keyStore, trustStore);
		return new PropertiesSslBundle(storeBundle, properties);
	}

	private static @Nullable PemSslStore getPemSslStore(String propertyName, PemSslBundleProperties.Store properties,
			ResourceLoader resourceLoader) {
		PemSslStoreDetails details = asPemSslStoreDetails(properties);
		PemSslStore pemSslStore = PemSslStore.load(details, resourceLoader);
		if (properties.isVerifyKeys()) {
			Assert.state(pemSslStore != null, "'pemSslStore' must not be null");
			PrivateKey privateKey = pemSslStore.privateKey();
			Assert.state(privateKey != null, "'privateKey' must not be null");
			CertificateMatcher certificateMatcher = new CertificateMatcher(privateKey);
			List<X509Certificate> certificates = pemSslStore.certificates();
			Assert.state(certificates != null, "'certificates' must not be null");
			Assert.state(certificateMatcher.matchesAny(certificates),
					() -> "Private key in %s matches none of the certificates in the chain".formatted(propertyName));
		}
		return pemSslStore;
	}

	private static PemSslStoreDetails asPemSslStoreDetails(PemSslBundleProperties.Store properties) {
		return new PemSslStoreDetails(properties.getType(), properties.getCertificate(), properties.getPrivateKey(),
				properties.getPrivateKeyPassword());
	}

	/**
	 * Get an {@link SslBundle} for the given {@link JksSslBundleProperties}.
	 * @param properties the source properties
	 * @return an {@link SslBundle} instance
	 */
	public static SslBundle get(JksSslBundleProperties properties) {
		return get(properties, ApplicationResourceLoader.get());
	}

	/**
	 * Get an {@link SslBundle} for the given {@link JksSslBundleProperties}.
	 * @param properties the source properties
	 * @param resourceLoader the resource loader used to load content
	 * @return an {@link SslBundle} instance
	 * @since 3.3.5
	 */
	public static SslBundle get(JksSslBundleProperties properties, ResourceLoader resourceLoader) {
		SslStoreBundle storeBundle = asSslStoreBundle(properties, resourceLoader);
		return new PropertiesSslBundle(storeBundle, properties);
	}

	private static SslStoreBundle asSslStoreBundle(JksSslBundleProperties properties, ResourceLoader resourceLoader) {
		JksSslStoreDetails keyStoreDetails = asStoreDetails(properties.getKeystore());
		JksSslStoreDetails trustStoreDetails = asStoreDetails(properties.getTruststore());
		return new JksSslStoreBundle(keyStoreDetails, trustStoreDetails, resourceLoader);
	}

	private static JksSslStoreDetails asStoreDetails(JksSslBundleProperties.Store properties) {
		return new JksSslStoreDetails(properties.getType(), properties.getProvider(), properties.getLocation(),
				properties.getPassword());
	}

	@Override
	public String toString() {
		ToStringCreator creator = new ToStringCreator(this);
		creator.append("key", this.key);
		creator.append("options", this.options);
		creator.append("protocol", this.protocol);
		creator.append("stores", this.stores);
		return creator.toString();
	}

}
