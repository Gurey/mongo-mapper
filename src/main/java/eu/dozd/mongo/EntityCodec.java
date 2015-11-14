package eu.dozd.mongo;

import org.bson.*;
import org.bson.assertions.Assertions;
import org.bson.codecs.*;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Codec used to decode and encode registered entities.
 */
class EntityCodec<T> implements CollectibleCodec<T> {
    private final Class<T> clazz;
    private final EntityInfo info;
    private final IdGenerator idGenerator;
    private final DocumentCodec documentCodec;
    private final BsonTypeClassMap bsonTypeClassMap;
    private final CodecRegistry registry;

    public EntityCodec(Class<T> clazz, EntityInfo info) {
        this.clazz = clazz;
        this.info = info;
        idGenerator = Assertions.notNull("idGenerator", new ObjectIdGenerator());
        registry = CodecRegistries.fromProviders(MongoMapper.getProviders());
        documentCodec = new DocumentCodec(registry, new BsonTypeClassMap());
        bsonTypeClassMap = new BsonTypeClassMap();
    }

    @Override
    public T generateIdIfAbsentFromDocument(T t) {
        if (!documentHasId(t)) {
            info.setId(t, idGenerator.generate().toString());
        }
        return t;
    }

    @Override
    public boolean documentHasId(T t) {
        if (info.getIdColumn() != null) {
            String id = info.getId(t);
            return (id != null);
        }
        return false;
    }

    @Override
    public BsonValue getDocumentId(T t) {
        String id = info.getId(t);
        return new BsonObjectId(new ObjectId(id));
    }

    @Override
    public T decode(BsonReader bsonReader, DecoderContext decoderContext) {
        Document document = new Document();

        bsonReader.readStartDocument();

        while (bsonReader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            String fieldName = bsonReader.readName();
            if (info.isMappedReference(fieldName)) {
                document.put(fieldName, registry.get(info.getFieldType(fieldName)).decode(bsonReader, decoderContext));
            } else {
                document.put(fieldName, readValue(bsonReader, decoderContext));
            }
        }

        bsonReader.readEndDocument();

        T t;
        try {
            t = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new MongoMapperException("Cannot create instance of class " + clazz.getName(), e);
        }

        for (String field : info.getFields()) {
            if (field.equals(info.getIdColumn())) {
                info.setId(t, (String) document.get("_id"));
            } else {
                Object o;
                o = document.get(field);
                info.setValue(t, field, o);
            }
        }

        return t;
    }

    @Override
    public void encode(BsonWriter bsonWriter, T t, EncoderContext encoderContext) {
        Document document = new Document();

        for (String field : info.getFields()) {
            if (field.equals(info.getIdColumn())) {
                if (documentHasId(t)) {
                    document.put("_id", info.getId(t));
                }
            } else {
                document.put(field, info.getValue(t, field));
            }
        }

        this.documentCodec.encode(bsonWriter, document, encoderContext);
    }

    @Override
    public Class<T> getEncoderClass() {
        return clazz;
    }

    private Object readValue(final BsonReader reader, final DecoderContext decoderContext) {
        BsonType bsonType = reader.getCurrentBsonType();
        if (bsonType == BsonType.NULL) {
            reader.readNull();
            return null;
        } else if (bsonType == BsonType.ARRAY) {
            return readList(reader, decoderContext);
        } else if (bsonType == BsonType.BINARY) {
            byte bsonSubType = reader.peekBinarySubType();
            if (bsonSubType == BsonBinarySubType.UUID_STANDARD.getValue() || bsonSubType == BsonBinarySubType.UUID_LEGACY.getValue()) {
                return registry.get(UUID.class).decode(reader, decoderContext);
            }
        }
        return registry.get(bsonTypeClassMap.get(bsonType)).decode(reader, decoderContext);
    }

    private List<Object> readList(final BsonReader reader, final DecoderContext decoderContext) {
        reader.readStartArray();
        List<Object> list = new ArrayList<Object>();
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            list.add(readValue(reader, decoderContext));
        }
        reader.readEndArray();
        return list;
    }
}