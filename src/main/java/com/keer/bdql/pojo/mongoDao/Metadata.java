package com.keer.bdql.pojo.mongoDao;

import com.keer.bdql.pojo.Table;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

/**
 * 附加数据集
 */
@Document(collection = "metadata")
public class Metadata implements Serializable {
    @Id
    private String _id;
    @Field(value = "id")
    private String id;
    @Field(value = "metadata")
    private TableData metadata;

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TableData getMetadata() {
        return metadata;
    }

    public void setMetadata(TableData metadata) {
        this.metadata = metadata;
    }
}
