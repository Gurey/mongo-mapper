package eu.dozd.mongo;

import eu.dozd.mongo.entity.TestEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EntityInfoTest {

    private TestEntity entity;
    private EntityInfo info;

    @Before
    public void setUp() throws Exception {
        entity = new TestEntity();
        info = new EntityInfo(TestEntity.class);
    }

    @Test
    public void testGetId() throws Exception {
        Assert.assertNull(info.getId(null));
    }

    @Test
    public void testGetIdField() throws Exception {
        Assert.assertNull(info.getIdField());
    }

    @Test
    public void testSetId() throws Exception {
        info.setId(null, null);
    }
}
