package com.ecsite.config.json;

import com.ecsite.common.model.base.EntityId;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

/**
 * 識別子の型の JSON の設定
 *
 * <p>{@link EntityId} を実装する record を、UUID の文字列として書く。読むときは、UUID の文字列から、その record の UUID 1 つのコンストラクターで作る。
 * Spring Boot が、Bean のこのモジュールをアプリの {@code JsonMapper} に登録する。
 * domain の型には Jackson の注釈を付けない。
 */
@Component
public class EntityIdModule extends SimpleModule {

    /**
     * モジュールを作り、書き方を登録する。
     */
    public EntityIdModule() {
        super("EntityIdModule");
        addSerializer(EntityId.class, new EntityIdSerializer());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addDeserializers(new EntityIdDeserializers());
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
            return EntityId.class.isAssignableFrom(valueType) && valueType.isRecord();
        }

        @Override
        public @Nullable ValueDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config, BeanDescription.Supplier beanDescRef) {
            Class<?> raw = type.getRawClass();
            if (!EntityId.class.isAssignableFrom(raw) || !raw.isRecord()) {
                return null;
            }
            try {
                Constructor<?> constructor = raw.getDeclaredConstructor(UUID.class);
                constructor.setAccessible(true);
                return new EntityIdDeserializer(raw, constructor);
            } catch (NoSuchMethodException e) {
                // UUID 1 つのコンストラクターがない型は、読み方を決めない
                return null;
            }
        }
    }

    /**
     * UUID の文字列から、{@link EntityId} の実装を作る。
     */
    private static final class EntityIdDeserializer extends ValueDeserializer<Object> {

        private final Class<?> type;
        private final Constructor<?> constructor;

        EntityIdDeserializer(Class<?> type, Constructor<?> constructor) {
            this.type = type;
            this.constructor = constructor;
        }

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
