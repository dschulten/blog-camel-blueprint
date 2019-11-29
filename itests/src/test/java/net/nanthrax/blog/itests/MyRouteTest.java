package net.nanthrax.blog.itests;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.karaf.itests.KarafTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

import java.util.stream.Stream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.maven;

@RunWith(PaxExam.class)
public class MyRouteTest extends KarafTestSupport {

    private DefaultCamelContext context;
    private ProducerTemplate template;

    @Before
    public void setUp() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder() {
            public void configure() {
                from("file:camel-output").to("mock:itest");
            }
        };

        context = new DefaultCamelContext();
        context.setName("context-test");
        context.addRoutes(routeBuilder);
        context.start();
        template = context.createProducerTemplate();
    }

    @Configuration
    public Option[] config() {

        Option[] options = new Option[]{
                KarafDistributionOption.features(maven().groupId("org.apache.camel.karaf")
                        .artifactId("apache-camel")
                        .type("xml")
                        .classifier("features")
                        .versionAsInProject(), "camel-blueprint"),

                KarafDistributionOption.features(maven().groupId("net.nanthrax.blog")
                        .artifactId("camel-blueprint")
                        .type("xml")
                        .classifier("features")
                        .version("1.0-SNAPSHOT"), "blog-camel-blueprint-route"),
        };
        return Stream.of(super.config(), options)
                .flatMap(Stream::of)
                .toArray(Option[]::new);
    }

    @Test
    public void testProvisioning() throws Exception {
        // first check that the features are installed
        assertTrue(featureService.isInstalled(featureService.getFeature("camel-blueprint")));
        assertTrue(featureService.isInstalled(featureService.getFeature("camel-core")));

        // now we check if the OSGi services corresponding to the camel context and route are there
        assertTrue(featureService.isInstalled(featureService.getFeature("blog-camel-blueprint-route")));
    }

    @Test
    public void testMyRoute() throws Exception {
        MockEndpoint itestMock = getMockEndpoint("mock:itest", true);
        itestMock.expectedMinimumMessageCount(3);
        itestMock.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) {
                System.out.println(exchange.getIn().getBody(String.class));
            }
        });

        template.start();

        Thread.sleep(20000);

        MockEndpoint.assertIsSatisfied(context);
    }

    private MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        if (create) {
            return resolveMandatoryEndpoint(context, uri, MockEndpoint.class);
        } else {
            Endpoint endpoint = this.context.hasEndpoint(uri);
            if (endpoint instanceof MockEndpoint) {
                return (MockEndpoint) endpoint;
            } else {
                throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
            }
        }
    }


    private <T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String uri,
                                                           Class<T> endpointType) {
        T endpoint = context.getEndpoint(uri, endpointType);

        assertNotNull("No endpoint found for URI: " + uri, endpoint);

        return endpoint;
    }

}
