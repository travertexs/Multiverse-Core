package org.mvplugins.multiverse.core.configuration.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.dumptruckman.minecraft.util.Logging;
import io.vavr.Value;
import io.vavr.control.Try;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.mvplugins.multiverse.core.configuration.functions.DefaultStringParserProvider;
import org.mvplugins.multiverse.core.configuration.functions.DefaultSuggesterProvider;
import org.mvplugins.multiverse.core.configuration.functions.NodeSerializer;
import org.mvplugins.multiverse.core.configuration.functions.NodeStringParser;
import org.mvplugins.multiverse.core.configuration.functions.NodeSuggester;

public class ListConfigNode<I> extends ConfigNode<List<I>> implements ListValueNode<I> {

    /**
     * Creates a new builder for a {@link ConfigNode}.
     *
     * @param path  The path of the node.
     * @param type  The type of the value.
     * @param <I>   The type of the value.
     * @return The new builder.
     */
    public static @NotNull <I> Builder<I, ? extends Builder<I, ?>> listBuilder(
            @NotNull String path,
            @NotNull Class<I> type) {
        return new Builder<>(path, type);
    }

    protected final Class<I> itemType;
    protected final NodeSuggester itemSuggester;
    protected final NodeStringParser<I> itemStringParser;
    protected final NodeSerializer<I> itemSerializer;
    protected final Function<I, Try<Void>> itemValidator;
    protected final BiConsumer<I, I> onSetItemValue;

    protected ListConfigNode(
            @NotNull String path,
            @NotNull String[] comments,
            @Nullable String name,
            @NotNull Class<List<I>> type,
            @Nullable Supplier<List<I>> defaultValueSupplier,
            @Nullable NodeSuggester suggester,
            @Nullable NodeStringParser<List<I>> stringParser,
            @Nullable NodeSerializer<List<I>> serializer,
            @Nullable Function<List<I>, Try<Void>> validator,
            @Nullable BiConsumer<List<I>, List<I>> onSetValue,
            @NotNull Class<I> itemType,
            @Nullable NodeSuggester itemSuggester,
            @Nullable NodeStringParser<I> itemStringParser,
            @Nullable NodeSerializer<I> itemSerializer,
            @Nullable Function<I, Try<Void>> itemValidator,
            @Nullable BiConsumer<I, I> onSetItemValue) {
        super(path, comments, name, type, defaultValueSupplier, suggester, stringParser, serializer,
                validator, onSetValue);
        this.itemType = itemType;
        this.itemSuggester = itemSuggester != null
                ? itemSuggester
                : DefaultSuggesterProvider.getDefaultSuggester(itemType);
        this.itemStringParser = itemStringParser != null
                ? itemStringParser
                : DefaultStringParserProvider.getDefaultStringParser(itemType);
        this.itemSerializer = itemSerializer;
        this.itemValidator = itemValidator;
        this.onSetItemValue = onSetItemValue;

        if (this.itemSuggester != null && this.suggester == null) {
            setDefaultSuggester();
        }
        if (this.itemStringParser != null && this.stringParser == null) {
            Logging.fine("Setting default string parser for list node " + path);
            setDefaultStringParser();
        }
        if (this.itemValidator != null && this.validator == null) {
            setDefaultValidator();
        }
        if (this.itemSerializer != null && this.serializer == null) {
            setDefaultSerialiser();
        }
        if (this.onSetItemValue != null && this.onSetValue == null) {
            setDefaultOnSetValue();
        }
    }

    private void setDefaultSuggester() {
        this.suggester = input -> {
            int lastIndexOf = input.lastIndexOf(',');
            if (lastIndexOf == -1) {
                return itemSuggester.suggest(input);
            }

            String lastInput = input.substring(lastIndexOf + 1);
            String inputBeforeLast = input.substring(0, lastIndexOf + 1);
            Set<String> inputs = Set.of(inputBeforeLast.split(","));
            return itemSuggester.suggest(lastInput).stream()
                    .filter(item -> !inputs.contains(item))
                    .map(item -> inputBeforeLast + item)
                    .toList();
        };
    }

    private void setDefaultStringParser() {
        this.stringParser = (input, type) -> {
            if (input == null) {
                return Try.failure(new IllegalArgumentException("Input cannot be null"));
            }
            return Try.sequence(Arrays.stream(input.split(","))
                    .map(inputItem -> itemStringParser.parse(inputItem, itemType))
                    .toList()).map(Value::toJavaList);
        };
    }

    private void setDefaultValidator() {
        this.validator = value -> {
            if (value != null) {
                return Try.sequence(value.stream().map(itemValidator).toList()).map(v -> null);
            }
            return Try.success(null);
        };
    }

    private void setDefaultSerialiser() {
        this.serializer = new NodeSerializer<>() {
            @Override
            public List<I> deserialize(Object object, Class<List<I>> type) {
                if (object instanceof List list) {
                    return list.stream().map(item -> itemSerializer.deserialize(item, itemType)).toList();
                }
                return new ArrayList<>();
            }

            @Override
            public Object serialize(List<I> object, Class<List<I>> type) {
                if (object != null) {
                    return object.stream().map(item -> itemSerializer.serialize(item, itemType)).toList();
                }
                return new ArrayList<>();
            }
        };
    }

    private void setDefaultOnSetValue() {
        this.onSetValue = (oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.stream()
                        .filter(value -> !newValue.contains(value))
                        .forEach(item -> onSetItemValue.accept(item, null));
            }
            newValue.forEach(item -> onSetItemValue.accept(null, item));
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Class<I> getItemType() {
        return itemType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Collection<String> suggestItem(@Nullable String input) {
        if (itemSuggester != null) {
            return itemSuggester.suggest(input);
        }
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull Try<I> parseItemFromString(@Nullable String input) {
        if (itemStringParser != null) {
            return itemStringParser.parse(input, itemType);
        }
        return Try.failure(new UnsupportedOperationException("No item string parser for type " + itemType));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable NodeSerializer<I> getItemSerializer() {
        return itemSerializer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Try<Void> validateItem(@Nullable I value) {
        if (itemValidator != null) {
            return itemValidator.apply(value);
        }
        return Try.success(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSetItemValue(@Nullable I oldValue, @Nullable I newValue) {
        if (onSetItemValue != null) {
            onSetItemValue.accept(oldValue, newValue);
        }
    }

    public static class Builder<I, B extends ListConfigNode.Builder<I, B>> extends ConfigNode.Builder<List<I>, B> {

        protected final @NotNull Class<I> itemType;
        protected @Nullable NodeSuggester itemSuggester;
        protected @Nullable NodeStringParser<I> itemStringParser;
        protected @Nullable NodeSerializer<I> itemSerializer;
        protected @Nullable Function<I, Try<Void>> itemValidator;
        protected @Nullable BiConsumer<I, I> onSetItemValue;

        /**
         * Creates a new builder.
         *
         * @param path      The path of the node.
         * @param itemType  The type of the item value in list.
         */
        protected Builder(@NotNull String path, @NotNull Class<I> itemType) {
            //noinspection unchecked
            super(path, (Class<List<I>>) (Object) List.class);
            this.itemType = itemType;
            this.defaultValueSupplier = () -> (List<I>) new ArrayList<Object>();
        }

        public @NotNull B itemSuggester(@NotNull NodeSuggester itemSuggester) {
            this.itemSuggester = itemSuggester;
            return self();
        }

        public @NotNull B itemStringParser(@NotNull NodeStringParser<I> itemStringParser) {
            this.itemStringParser = itemStringParser;
            return self();
        }

        /**
         * Sets the serializer for the node.
         *
         * @param serializer The serializer for the node.
         * @return This builder.
         */
        public @NotNull B itemSerializer(@NotNull NodeSerializer<I> serializer) {
            this.itemSerializer = serializer;
            return self();
        }

        /**
         * Sets the validator for the node.
         *
         * @param itemValidator The validator for the node.
         * @return This builder.
         */
        public @NotNull B itemValidator(@NotNull Function<I, Try<Void>> itemValidator) {
            this.itemValidator = itemValidator;
            return self();
        }

        /**
         * Sets the onSetValue for the node.
         *
         * @param onSetItemValue    The onSetValue for the node.
         * @return This builder.
         */
        public @NotNull B onSetItemValue(@Nullable BiConsumer<I, I> onSetItemValue) {
            this.onSetItemValue = onSetItemValue;
            return self();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public @NotNull ListConfigNode<I> build() {
            return new ListConfigNode<>(
                    path,
                    comments.toArray(new String[0]),
                    name,
                    type,
                    defaultValueSupplier,
                    suggester,
                    stringParser,
                    serializer,
                    validator,
                    onSetValue,
                    itemType,
                    itemSuggester, itemStringParser, itemSerializer,
                    itemValidator,
                    onSetItemValue);
        }
    }
}