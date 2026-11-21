package com.ecsite.config.json;

import com.ecsite.common.model.base.EntityId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.Serializers;
import tools.jackson.databind.ser.std.StdSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * 識別子の型の JSON の設定
 *
 * <p>{@link EntityId} を実装する、UUID 1 つのコンストラクターを持つ record を、UUID の文字列として書く。
 * 読むときは、UUID の文字列から、そのコンストラクターで作る。書く型と読む型は同じ。
 * Spring Boot が、Bean のこのモジュールをアプリの {@code JsonMapper} に登録する。
 * domain の型には Jackson の注釈を付けない。
 */
@Component
class EntityIdModule extends SimpleModule {

    /**
     * モジュールを作る。
     */
    EntityIdModule() {
        super("EntityIdModule");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializers(new EntityIdSerializers());
        context.addDeserializers(new EntityIdDeserializers());
    }

    /**
     * 書く側と読む側が対象にする型の、UUID 1 つのコンストラクターを返す。
     *
     * @param type 調べる型
     * @return {@link EntityId} を実装する、UUID 1 つのコンストラクターを持つ record ならそのコンストラクター。対象でなければ null
     */
    private static @Nullable Constructor<?> entityIdConstructor(Class<?> type) {
        if (!EntityId.class.isAssignableFrom(type) || !type.isRecord()) {
            return null;
        }
        try {
            return type.getDeclaredConstructor(UUID.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * {@link EntityId} の実装の書き方を返す。
     */
    private static final class EntityIdSerializers extends Serializers.Base {

        @Override
        public @Nullable ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type,
                                                           BeanDescription.Supplier beanDescRef, JsonFormat.Value formatOverrides) {
            return entityIdConstructor(type.getRawClass()) != null ? new EntityIdSerializer() : null;
        }
    }

    /**
     * {@link EntityId} の実装を、UUID の文字列で書く。
     */
    private static final class EntityIdSerializer extends StdSerializer<EntityId> {

        EntityIdSerializer() {
            super(EntityId.class);
        }

        @Override
        public void serialize(EntityId value, JsonGenerator gen, SerializationContext provider) {
            gen.writeString(value.value().toString());
        }
    }

    /**
     * {@link EntityId} の実装の読み方を返す。
     */
    private static final class EntityIdDeserializers extends Deserializers.Base {

        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            return entityIdConstructor(valueType) != null;
        }

        @Override
        public @Nullable ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config, BeanDescription.Supplier beanDescRef) {
            Class<?> raw = type.getRawClass();
            Constructor<?> constructor = entityIdConstructor(raw);
            if (constructor == null) {
                return null;
            }
            constructor.setAccessible(true);
            return new EntityIdDeserializer(raw, constructor);
        }
    }

    /**
     * UUID の文字列から、{@link EntityId} の実装を作る。
     */
    @RequiredArgsConstructor
    private static final class EntityIdDeserializer extends ValueDeserializer<Object> {

        private final Class<?> type;
        private final Constructor<?> constructor;

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getValueAsString();
            UUID uuid;
            try {
                uuid = UUID.fromString(text);
            } catch (IllegalArgumentException | NullPointerException e) {
                return ctxt.handleWeirdStringValue(type, text, "UUID の文字列ではない");
            }
            try {
                return constructor.newInstance(uuid);
            } catch (InvocationTargetException e) {
                return ctxt.handleInstantiationProblem(type, uuid, e.getCause());
            } catch (ReflectiveOperationException e) {
                return ctxt.handleInstantiationProblem(type, uuid, e);
            }
        }
    }
}
