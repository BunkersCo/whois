package net.ripe.db.whois.api.whois;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import net.ripe.db.whois.api.AbstractRestClientTest;
import net.ripe.db.whois.api.httpserver.Audience;
import net.ripe.db.whois.api.whois.domain.WhoisResources;
import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.domain.CIString;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Category(IntegrationTest.class)
public class WhoisRdapServiceAutnum extends AbstractRestClientTest {

    private static final Audience AUDIENCE = Audience.RDAP;

    private static final RpslObject TP1_TEST_BOOT =
        RpslObject.parse("person: Test Person\nnic-hdl: TP1-TEST");

    private static final RpslObject TP1_TEST = 
        RpslObject.parse(
            "person:  Test Person\n" +
            "address: Test Address\n" +
            "phone:   +61-1234-1234\n" +
            "e-mail:  noreply@ripe.net\n" +
            "mnt-by:  OWNER-MNT\n" +
            "nic-hdl: TP1-TEST\n" +
            "changed: noreply@ripe.net 20120101\n" +
            "source:  TEST\n"
        );

    private static final RpslObject OWNER_MNT = 
        RpslObject.parse(
            "mntner:      OWNER-MNT\n" +
            "descr:       Owner Maintainer\n" +
            "admin-c:     TP1-TEST\n" +
            "upd-to:      noreply@ripe.net\n" +
            "auth:        MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
            "mnt-by:      OWNER-MNT\n" +
            "referral-by: OWNER-MNT\n" +
            "changed:     dbtest@ripe.net 20120101\n" +
            "source:      TEST"
        );

    private static final RpslObject ASN_RANGE_ONE =
        RpslObject.parse(
            "as-block:  AS1000-AS2000\n" +
            "descr:     An ASN range\n" +
            "admin-c:   TP1-TEST\n" +
            "tech-c:    TP1-TEST\n" +
            "country:   AU\n" +
            "changed:   test@test.net.au 20010816\n" +
            "mnt-by:    OWNER-MNT\n" +
            "source:    TEST\n"
        );

    private static final RpslObject ASN_RANGE_TWO =
        RpslObject.parse(
            "as-block:  AS10000-AS20000\n" +
            "descr:     An ASN range\n" +
            "admin-c:   TP1-TEST\n" +
            "tech-c:    TP1-TEST\n" +
            "country:   AU\n" +
            "changed:   test@test.net.au 20010816\n" +
            "mnt-by:    OWNER-MNT\n" +
            "source:    TEST\n"
        );

    private static final RpslObject ASN_SINGLE =
        RpslObject.parse(
            "aut-num:   AS12345\n" +
            "as-name:   AS-TEST\n" +
            "descr:     A single ASN\n" +
            "admin-c:   TP1-TEST\n" +
            "tech-c:    TP1-TEST\n" +
            "country:   AU\n" +
            "changed:   test@test.net.au 20010816\n" +
            "mnt-by:    OWNER-MNT\n" +
            "source:    TEST\n"
        );

    @Before
    public void setup() throws Exception {
        databaseHelper.addObject(TP1_TEST_BOOT);
        databaseHelper.addObject(OWNER_MNT);
        databaseHelper.updateObject(TP1_TEST);

        List<RpslObject> objects = 
            new ArrayList<RpslObject>(Arrays.asList(
                ASN_RANGE_ONE,
                ASN_RANGE_TWO,
                ASN_SINGLE
            ));

        for (RpslObject o : objects) {
            databaseHelper.addObject(o);
        }
    }

    @Before
    @Override
    public void setUpClient() throws Exception {
        ClientConfig cc = new DefaultClientConfig();
        cc.getSingletons().add(new JacksonJaxbJsonProvider().configure(DeserializationConfig.Feature.UNWRAP_ROOT_VALUE, true));
        client = Client.create(cc);
    }

    @Test
    public void noAutnum() throws Exception {
        final ClientResponse cr = 
            createResource(AUDIENCE, "autnum/1")
                .get(ClientResponse.class);
        assertThat(cr.getStatus(), equalTo(404));
    }

    @Test
    public void lookupSingleAutnum() throws Exception {
        final ClientResponse cr = 
            createResource(AUDIENCE, "autnum/12345")
                .get(ClientResponse.class);
        String res = cr.getEntity(String.class);
        assertThat(res, containsString("\"handle\" : \"AS12345\""));
        assertThat(res, containsString("\"startAutnum\" : 12345,"));
        assertThat(res, containsString("\"endAutnum\" : 12345,"));
        assertThat(res, containsString("\"name\" : \"AS-TEST\""));
        assertThat(res, containsString("\"country\" : \"AU\""));
        assertThat(res, containsString("\"type\" : \"DIRECT ALLOCATION\""));
    }

    @Test
    public void lookupAutnumWithinBlock() throws Exception {
        final ClientResponse cr = 
            createResource(AUDIENCE, "autnum/1500")
                .get(ClientResponse.class);
        String res = cr.getEntity(String.class);
        assertThat(res, containsString("\"handle\" : \"AS1000-AS2000\""));
        assertThat(res, containsString("\"startAutnum\" : 1000,"));
        assertThat(res, containsString("\"endAutnum\" : 2000,"));
        assertThat(res, containsString("\"name\" : \"AS1000-AS2000\""));
        assertThat(res, containsString("\"country\" : \"AU\""));
        assertThat(res, containsString("\"type\" : \"DIRECT ALLOCATION\""));
    }

    @Override
    protected WebResource createResource(final Audience audience, final String path) {
        return client.resource(String.format("http://localhost:%s/%s", getPort(audience), path));
    }
}
