package org.flatscrew.latte;

public record UpdateResult<M extends Model>(M model, Cmd cmd) {

    public static <M extends Model> UpdateResult<M> of(M model) {
        return new UpdateResult<>(model, null);
    }
}
