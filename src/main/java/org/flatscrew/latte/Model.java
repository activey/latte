package org.flatscrew.latte;

public interface Model {

    Cmd init();
    UpdateResult<? extends Model> update(Message msg);
    String view();
}
