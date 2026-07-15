package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntitySnapshotTest
{
    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void constructor_shouldRejectNullUuid()
    {
        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot(
            null, "minecraft:zombie", new Vec3(0, 0, 0), null, List.of(), null, 0L))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("uuid");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void constructor_shouldRejectNullTypeKey()
    {
        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot(
            UUID.randomUUID(), null, new Vec3(0, 0, 0), null, List.of(), null, 0L))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("typeKey");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void constructor_shouldRejectNullPosition()
    {
        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot(
            UUID.randomUUID(), "minecraft:zombie", null, null, List.of(), null, 0L))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("position");
    }

    @Test
    void pdcKeys_shouldNotExposeConstructorSourceList()
    {
        // setup
        final List<String> source = new ArrayList<>(List.of("plugin:alpha"));
        final EntitySnapshot snapshot = new EntitySnapshot(
            UUID.randomUUID(), "minecraft:zombie", new Vec3(0, 0, 0), null, source, null, 0L);

        // execute
        source.add("plugin:beta");

        // verify
        assertThat(snapshot.pdcKeys()).containsExactly("plugin:alpha");
        assertThatThrownBy(() -> snapshot.pdcKeys().add("plugin:gamma"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pdcKeys_shouldBeSortedRegardlessOfSourceOrder()
    {
        // setup
        final List<String> source = List.of("plugin:gamma", "plugin:alpha", "plugin:beta");

        // execute
        final EntitySnapshot snapshot = new EntitySnapshot(
            UUID.randomUUID(), "minecraft:zombie", new Vec3(0, 0, 0), null, source, null, 0L);

        // verify
        assertThat(snapshot.pdcKeys()).containsExactly("plugin:alpha", "plugin:beta", "plugin:gamma");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void pdcKeys_shouldDefaultToEmptyListWhenConstructedWithNull()
    {
        // setup + execute
        final EntitySnapshot snapshot = new EntitySnapshot(
            UUID.randomUUID(), "minecraft:zombie", new Vec3(0, 0, 0), null, null, null, 0L);

        // verify
        assertThat(snapshot.pdcKeys()).isEmpty();
    }

    @Test
    void constructor_shouldStoreAllFields()
    {
        // setup
        final UUID uuid = UUID.randomUUID();
        final Vec3 position = new Vec3(1.0, 2.0, 3.0);

        // execute
        final EntitySnapshot snapshot = new EntitySnapshot(
            uuid, "minecraft:zombie", position, "Bob", List.of("plugin:alpha"), null, 42L);

        // verify
        assertThat(snapshot.uuid()).isEqualTo(uuid);
        assertThat(snapshot.typeKey()).isEqualTo("minecraft:zombie");
        assertThat(snapshot.position()).isEqualTo(position);
        assertThat(snapshot.customName()).isEqualTo("Bob");
        assertThat(snapshot.pdcKeys()).containsExactly("plugin:alpha");
        assertThat(snapshot.transform()).isNull();
        assertThat(snapshot.tick()).isEqualTo(42L);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void transformConstructor_shouldRejectNullTranslation()
    {
        // setup
        final EntitySnapshot.Rotation rotation = new EntitySnapshot.Rotation(0, 0, 0, 1);

        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot.Transform(null, new Vec3(1, 1, 1), rotation, rotation))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("translation");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void transformConstructor_shouldRejectNullScale()
    {
        // setup
        final EntitySnapshot.Rotation rotation = new EntitySnapshot.Rotation(0, 0, 0, 1);

        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot.Transform(new Vec3(0, 0, 0), null, rotation, rotation))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("scale");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void transformConstructor_shouldRejectNullLeftRotation()
    {
        // setup
        final EntitySnapshot.Rotation rotation = new EntitySnapshot.Rotation(0, 0, 0, 1);

        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot.Transform(
            new Vec3(0, 0, 0), new Vec3(1, 1, 1), null, rotation))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("leftRotation");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void transformConstructor_shouldRejectNullRightRotation()
    {
        // setup
        final EntitySnapshot.Rotation rotation = new EntitySnapshot.Rotation(0, 0, 0, 1);

        // execute + verify
        assertThatThrownBy(() -> new EntitySnapshot.Transform(
            new Vec3(0, 0, 0), new Vec3(1, 1, 1), rotation, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("rightRotation");
    }

    @Test
    void transformConstructor_shouldStoreAllComponents()
    {
        // setup
        final Vec3 translation = new Vec3(1, 2, 3);
        final Vec3 scale = new Vec3(1, 1, 1);
        final EntitySnapshot.Rotation leftRotation = new EntitySnapshot.Rotation(0.1, 0.2, 0.3, 0.4);
        final EntitySnapshot.Rotation rightRotation = new EntitySnapshot.Rotation(0.5, 0.6, 0.7, 0.8);

        // execute
        final EntitySnapshot.Transform transform =
            new EntitySnapshot.Transform(translation, scale, leftRotation, rightRotation);

        // verify
        assertThat(transform.translation()).isEqualTo(translation);
        assertThat(transform.scale()).isEqualTo(scale);
        assertThat(transform.leftRotation()).isEqualTo(leftRotation);
        assertThat(transform.rightRotation()).isEqualTo(rightRotation);
    }

    @Test
    void rotation_shouldStoreComponents()
    {
        // setup + execute
        final EntitySnapshot.Rotation rotation = new EntitySnapshot.Rotation(0.1, 0.2, 0.3, 0.4);

        // verify
        assertThat(rotation.x()).isEqualTo(0.1);
        assertThat(rotation.y()).isEqualTo(0.2);
        assertThat(rotation.z()).isEqualTo(0.3);
        assertThat(rotation.w()).isEqualTo(0.4);
    }
}
