package org.apereo.cas.configuration.model.support.geo;

import org.apereo.cas.configuration.model.SpringResourceProperties;
import org.apereo.cas.configuration.model.support.geo.googlemaps.GoogleMapsProperties;
import org.apereo.cas.configuration.model.support.geo.maxmind.MaxmindProperties;
import org.apereo.cas.configuration.support.RequiresModule;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.io.Serializable;

/**
 * This is {@link GeoLocationProperties}.
 *
 * @author Misagh Moayyed
 * @since 6.6.0
 */
@Getter
@Setter
@Accessors(chain = true)
@RequiresModule(name = "cas-server-support-geolocation")
public class GeoLocationProperties implements Serializable {
    private static final long serialVersionUID = 7529478582792969209L;

    /**
     * MaxMind settings.
     */
    @NestedConfigurationProperty
    private MaxmindProperties maxmind = new MaxmindProperties();

    /**
     * Google Maps settings.
     */
    @NestedConfigurationProperty
    private GoogleMapsProperties googleMaps = new GoogleMapsProperties();

    /**
     * Groovy settings.
     */
    @NestedConfigurationProperty
    private SpringResourceProperties groovy = new SpringResourceProperties();
}
