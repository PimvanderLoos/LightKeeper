package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtocolValueEncoderTest
{
    private static ProtocolValueEncoder createEncoder(JavaPlugin plugin)
    {
        return new ProtocolValueEncoder(plugin);
    }

    @Test
    void encodeAccessors_shouldEncodeStringNumberBooleanUuidAndEnumLeaves()
    {
        // setup
        final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000042");
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new LeafFixture(id), "ctx");

        // verify
        assertThat(result)
            .containsEntry("getText", new IProtocolValue.PString("hello"))
            .containsEntry("getCount", new IProtocolValue.PNumber(5))
            .containsEntry("getRatio", new IProtocolValue.PNumber(2.5))
            .containsEntry("isActive", new IProtocolValue.PBool(true))
            .containsEntry("getId", new IProtocolValue.PUuid(id))
            .containsEntry("getKind", new IProtocolValue.PEnum(TestEnum.class.getName(), "ALPHA"));
    }

    @Test
    void encodeAccessors_shouldEncodeEntityAsPRef()
    {
        // setup
        final UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000043");
        final Entity entity = mock();
        when(entity.getUniqueId()).thenReturn(entityId);
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new EntityHolder(entity), "ctx");

        // verify
        assertThat(result).containsEntry(
            "getEntity", new IProtocolValue.PRef(entity.getClass().getName(), entityId.toString()));
    }

    @Test
    void encodeAccessors_shouldEncodeWorldAsPRef()
    {
        // setup
        final World world = mock();
        when(world.getName()).thenReturn("world_test");
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new WorldHolder(world), "ctx");

        // verify
        assertThat(result).containsEntry(
            "getWorld", new IProtocolValue.PRef(world.getClass().getName(), "world_test"));
    }

    @Test
    void encodeAccessors_shouldEncodeLocationAsPVecLeafWithoutWalkingAccessors()
    {
        // setup — a null world verifies the leaf never touches getWorld()/getChunk()/getBlock()
        final org.bukkit.Location location = new org.bukkit.Location(null, 1.5, 64.0, -2.25);
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new LocationHolder(location), "ctx");

        // verify
        assertThat(result).containsEntry("getLocation", new IProtocolValue.PVec(1.5, 64.0, -2.25));
    }

    @Test
    void encodeAccessors_shouldEncodeLocationAsPVecEvenWhenDepthIsExhausted()
    {
        // setup — a self-nesting fixture forces the walk past MAX_DEPTH; a sibling getChild() at the same,
        // depth-exhausted level is dropped (see encodeAccessors_shouldDropValueBeyondMaxDepth), but the
        // position leaf must still resolve because it is checked before the depth cutoff.
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-location-depth"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute
        final Map<String, IProtocolValue> level0 =
            encoder.encodeAccessors(new LocationBearingNestingFixture(), "ctx");

        // verify — drill down MAX_DEPTH - 1 levels to reach the depth-exhausted record, then read its sibling
        IProtocolValue current = java.util.Objects.requireNonNull(level0.get("getChild"));
        for (int depth = 0; depth < ProtocolValueEncoder.MAX_DEPTH - 1; depth++)
        {
            assertThat(current).isInstanceOf(IProtocolValue.PRecord.class);
            current = java.util.Objects.requireNonNull(
                ((IProtocolValue.PRecord) current).fields().get("getChild"));
        }
        assertThat(current).isInstanceOfSatisfying(IProtocolValue.PRecord.class, record ->
        {
            assertThat(record.fields().get("getChild")).isInstanceOf(IProtocolValue.PDropped.class);
            assertThat(record.fields().get("getLocation")).isEqualTo(new IProtocolValue.PVec(7.0, 8.0, 9.0));
        });
    }

    @Test
    void encodeAccessors_shouldEncodeVectorAsPVecLeaf()
    {
        // setup
        final org.bukkit.util.Vector vector = new org.bukkit.util.Vector(4.0, 5.0, 6.0);
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new VectorHolder(vector), "ctx");

        // verify
        assertThat(result).containsEntry("getVector", new IProtocolValue.PVec(4.0, 5.0, 6.0));
    }

    @Test
    void encodeAccessors_shouldWalkRecordComponents()
    {
        // setup
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new TestRecord("hello", 5), "ctx");

        // verify
        assertThat(result)
            .containsEntry("name", new IProtocolValue.PString("hello"))
            .containsEntry("value", new IProtocolValue.PNumber(5));
    }

    @Test
    void encodeAccessors_shouldSortAccessorsByNameForDeterministicOutput()
    {
        // setup
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new SortingFixture(), "ctx");

        // verify
        assertThat(result.keySet()).containsExactly("getA", "getB");
    }

    @Test
    void encodeAccessors_shouldOmitNullAccessorResults()
    {
        // setup
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new NullFieldFixture(), "ctx");

        // verify
        assertThat(result).doesNotContainKey("getMissing");
    }

    @Test
    void encodeAccessors_shouldEncodeListAsPList()
    {
        // setup
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new CollectionsFixture(), "ctx");

        // verify — a null element is skipped rather than encoded
        assertThat(result).containsEntry(
            "getItems",
            new IProtocolValue.PList(List.of(new IProtocolValue.PString("a"), new IProtocolValue.PString("b"))));
    }

    @Test
    void encodeAccessors_shouldEncodeArrayAsPList()
    {
        // setup
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new CollectionsFixture(), "ctx");

        // verify
        assertThat(result).containsEntry(
            "getTags",
            new IProtocolValue.PList(List.of(new IProtocolValue.PString("x"), new IProtocolValue.PString("y"))));
    }

    @Test
    void encodeAccessors_shouldEncodeMapAsPRecord()
    {
        // setup
        final ProtocolValueEncoder encoder = createEncoder(mock());

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new CollectionsFixture(), "ctx");

        // verify — a null-valued entry is skipped rather than encoded
        assertThat(result).containsEntry(
            "getMapping",
            new IProtocolValue.PRecord(Map.of("k1", new IProtocolValue.PString("v1"))));
    }

    @Test
    void encodeAccessors_shouldDropValueBeyondMaxDepth()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-depth"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute — a self-nesting fixture forces the walk past MAX_DEPTH
        final Map<String, IProtocolValue> level0 = encoder.encodeAccessors(new SelfNestingFixture(), "ctx");

        // verify — drill down through MAX_DEPTH nested records to reach the dropped innermost value
        IProtocolValue current = java.util.Objects.requireNonNull(level0.get("getChild"));
        for (int depth = 0; depth < ProtocolValueEncoder.MAX_DEPTH; depth++)
        {
            assertThat(current).isInstanceOf(IProtocolValue.PRecord.class);
            current = java.util.Objects.requireNonNull(
                ((IProtocolValue.PRecord) current).fields().get("getChild"));
        }
        assertThat(current).isInstanceOf(IProtocolValue.PDropped.class);
        assertThat(((IProtocolValue.PDropped) current).accessorName()).isEqualTo("getChild");
    }

    @Test
    void encodeAccessors_shouldDropEmptyNestedObject()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-empty"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute
        final Map<String, IProtocolValue> result =
            encoder.encodeAccessors(new HasEmptyFixtureField(), "ctx");

        // verify
        assertThat(result).containsEntry(
            "getEmpty", new IProtocolValue.PDropped("getEmpty", EmptyFixture.class.getName()));
    }

    @Test
    void encodeAccessors_shouldDropAndCaptureThrowingAccessor()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-throwing"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute
        final Map<String, IProtocolValue> result = encoder.encodeAccessors(new ThrowingFixture(), "ctx");

        // verify
        assertThat(result).containsEntry(
            "getBroken", new IProtocolValue.PDropped("getBroken", "capture-failed: IllegalStateException"));
    }

    @Test
    void encodeAccessors_shouldCapAccessorCountAndMarkTheOmittedRemainder()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-accessor-cap"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute
        final Map<String, IProtocolValue> result =
            encoder.encodeAccessors(new AccessorCapFixture(), "ctx");

        // verify — 35 getters are declared; the first 32 in sorted order survive, plus a loud marker
        assertThat(result).hasSize(ProtocolValueEncoder.MAX_ACCESSORS_PER_OBJECT + 1);
        assertThat(result).containsKey("getP00");
        assertThat(result).containsKey("getP31");
        assertThat(result).doesNotContainKey("getP32");
        assertThat(result.get(ProtocolValueEncoder.TRUNCATED_KEY)).isEqualTo(new IProtocolValue.PDropped(
            ProtocolValueEncoder.TRUNCATED_KEY, "truncated: 3 more accessors"));
    }

    @Test
    @SuppressWarnings("unchecked") // any(Supplier.class) necessarily returns a raw Supplier match.
    void encodeAccessors_shouldLogDropWarningOnlyOncePerContextAndAccessor()
    {
        // setup
        final JavaPlugin plugin = mock();
        final Logger logger = mock();
        when(plugin.getLogger()).thenReturn(logger);
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute — the same context+accessor pair drops twice across two separate captures
        encoder.encodeAccessors(new ThrowingFixture(), "same-context");
        encoder.encodeAccessors(new ThrowingFixture(), "same-context");

        // verify
        verify(logger, times(1)).log(eq(Level.WARNING), any(Supplier.class));
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    public enum TestEnum
    {
        ALPHA,
        BETA
    }

    public record TestRecord(String name, int value)
    {
    }

    public static final class LeafFixture
    {
        private final UUID id;

        public LeafFixture(UUID id)
        {
            this.id = id;
        }

        public String getText()
        {
            return "hello";
        }

        public int getCount()
        {
            return 5;
        }

        public double getRatio()
        {
            return 2.5;
        }

        public boolean isActive()
        {
            return true;
        }

        public UUID getId()
        {
            return id;
        }

        public TestEnum getKind()
        {
            return TestEnum.ALPHA;
        }
    }

    public static final class EntityHolder
    {
        private final Entity entity;

        public EntityHolder(Entity entity)
        {
            this.entity = entity;
        }

        public Entity getEntity()
        {
            return entity;
        }
    }

    public static final class WorldHolder
    {
        private final World world;

        public WorldHolder(World world)
        {
            this.world = world;
        }

        public World getWorld()
        {
            return world;
        }
    }

    public static final class LocationHolder
    {
        private final org.bukkit.Location location;

        public LocationHolder(org.bukkit.Location location)
        {
            this.location = location;
        }

        public org.bukkit.Location getLocation()
        {
            return location;
        }
    }

    public static final class VectorHolder
    {
        private final org.bukkit.util.Vector vector;

        public VectorHolder(org.bukkit.util.Vector vector)
        {
            this.vector = vector;
        }

        public org.bukkit.util.Vector getVector()
        {
            return vector;
        }
    }

    /**
     * Self-nesting fixture (like {@link SelfNestingFixture}) with an additional constant-valued
     * {@code getLocation()} accessor on every level, used to prove position leaves resolve even once
     * {@code remainingDepth} is exhausted by the {@code getChild()} recursion.
     */
    public static final class LocationBearingNestingFixture
    {
        public LocationBearingNestingFixture getChild()
        {
            return new LocationBearingNestingFixture();
        }

        public org.bukkit.Location getLocation()
        {
            return new org.bukkit.Location(null, 7.0, 8.0, 9.0);
        }
    }

    public static final class SortingFixture
    {
        public String getB()
        {
            return "b-value";
        }

        public String getA()
        {
            return "a-value";
        }
    }

    public static final class NullFieldFixture
    {
        public @Nullable String getMissing()
        {
            return null;
        }
    }

    public static final class CollectionsFixture
    {
        public List<String> getItems()
        {
            return Arrays.asList("a", null, "b");
        }

        public String[] getTags()
        {
            return new String[]{"x", "y"};
        }

        public Map<String, String> getMapping()
        {
            final Map<String, String> mapping = new LinkedHashMap<>();
            mapping.put("k1", "v1");
            mapping.put("k2", null);
            return mapping;
        }
    }

    public static final class SelfNestingFixture
    {
        public SelfNestingFixture getChild()
        {
            return new SelfNestingFixture();
        }
    }

    public static final class EmptyFixture
    {
    }

    public static final class HasEmptyFixtureField
    {
        public EmptyFixture getEmpty()
        {
            return new EmptyFixture();
        }
    }

    public static final class ThrowingFixture
    {
        public String getBroken()
        {
            throw new IllegalStateException("boom");
        }
    }

    public static final class AccessorCapFixture
    {
        public int getP00() { return 0; }
        public int getP01() { return 1; }
        public int getP02() { return 2; }
        public int getP03() { return 3; }
        public int getP04() { return 4; }
        public int getP05() { return 5; }
        public int getP06() { return 6; }
        public int getP07() { return 7; }
        public int getP08() { return 8; }
        public int getP09() { return 9; }
        public int getP10() { return 10; }
        public int getP11() { return 11; }
        public int getP12() { return 12; }
        public int getP13() { return 13; }
        public int getP14() { return 14; }
        public int getP15() { return 15; }
        public int getP16() { return 16; }
        public int getP17() { return 17; }
        public int getP18() { return 18; }
        public int getP19() { return 19; }
        public int getP20() { return 20; }
        public int getP21() { return 21; }
        public int getP22() { return 22; }
        public int getP23() { return 23; }
        public int getP24() { return 24; }
        public int getP25() { return 25; }
        public int getP26() { return 26; }
        public int getP27() { return 27; }
        public int getP28() { return 28; }
        public int getP29() { return 29; }
        public int getP30() { return 30; }
        public int getP31() { return 31; }
        public int getP32() { return 32; }
        public int getP33() { return 33; }
        public int getP34() { return 34; }
    }

    @Test
    void encodeAccessors_shouldExcludeStaticAccessors()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-static"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute
        final Map<String, IProtocolValue> encoded =
            encoder.encodeAccessors(new StaticAccessorFixture(), "ctx");

        // verify - the static accessor must not be walked into the payload
        assertThat(encoded).containsOnlyKeys("getInstanceValue");
    }

    @Test
    void encodeAccessors_shouldEncodeEnumConstantsWithClassBodiesAsEnums()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-enum-body"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);

        // execute
        final Map<String, IProtocolValue> encoded =
            encoder.encodeAccessors(new EnumWithBodyFixture(), "ctx");

        // verify - a constant with a body is a synthetic subclass; it must still encode as the declaring enum
        assertThat(encoded.get("getMode")).isEqualTo(
            new IProtocolValue.PEnum(BodiedEnum.class.getName(), "SPECIAL"));
    }

    @Test
    void encodeAccessors_shouldMarkTruncatedRemainderOfOversizedCollections()
    {
        // setup
        final JavaPlugin plugin = mock();
        when(plugin.getLogger()).thenReturn(Logger.getLogger("protocol-value-encoder-test-truncate"));
        final ProtocolValueEncoder encoder = createEncoder(plugin);
        final int oversize = ProtocolValueEncoder.MAX_CONTAINER_ELEMENTS + 5;

        // execute
        final Map<String, IProtocolValue> encoded =
            encoder.encodeAccessors(new OversizedListFixture(oversize), "ctx");

        // verify - the cap keeps the first MAX elements and reports the omitted remainder loudly
        assertThat(encoded.get("getEntries")).isInstanceOfSatisfying(IProtocolValue.PList.class, list ->
        {
            assertThat(list.values()).hasSize(ProtocolValueEncoder.MAX_CONTAINER_ELEMENTS + 1);
            assertThat(list.values().getLast()).isEqualTo(
                new IProtocolValue.PDropped("getEntries", "truncated: 5 more elements"));
        });
    }

    /**
     * Fixture with one instance accessor and one static accessor that must not be encoded.
     */
    public static final class StaticAccessorFixture
    {
        public String getInstanceValue()
        {
            return "instance";
        }

        public static String getStaticValue()
        {
            return "static";
        }
    }

    /**
     * Enum whose constant carries a class body, producing a synthetic subclass at runtime.
     */
    public enum BodiedEnum
    {
        SPECIAL
        {
            @Override
            public String describe()
            {
                return "special";
            }
        };

        public String describe()
        {
            return "plain";
        }
    }

    /**
     * Fixture exposing an enum-with-body value.
     */
    public static final class EnumWithBodyFixture
    {
        public BodiedEnum getMode()
        {
            return BodiedEnum.SPECIAL;
        }
    }

    /**
     * Fixture exposing a list larger than the container cap.
     */
    public static final class OversizedListFixture
    {
        private final java.util.List<String> entries;

        OversizedListFixture(int size)
        {
            entries = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++)
                entries.add("entry-" + i);
        }

        public java.util.List<String> getEntries()
        {
            return entries;
        }
    }
}
