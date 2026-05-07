package dev.sbs.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Optional;

/**
 * Serializable snapshot of a {@link dev.sbs.discordapi.response.Response Response}'s
 * mutable navigation state.
 *
 * <p>
 * Captures the current page identifier, the current paginated item index, and
 * the ordered history of visited page identifiers used by back-navigation.
 */
@Getter
@RequiredArgsConstructor
public final class NavState implements Serializable {

    /** Identifier of the page currently displayed, if any. */
    private final @NotNull Optional<String> currentPageId;

    /** Zero-based index into the current page's paginated item list. */
    private final int currentItemPage;

    /** Ordered history of visited page identifiers for back-navigation. */
    private final @NotNull ConcurrentList<String> pageHistory;

    /** Returns an empty navigation state used as the default for new responses. */
    public static @NotNull NavState empty() {
        return new NavState(Optional.empty(), 0, Concurrent.newList());
    }

    /** Returns a copy of this state with the given current page id. */
    public @NotNull NavState withCurrentPageId(@NotNull String pageId) {
        ConcurrentList<String> history = Concurrent.newList(this.pageHistory);
        return new NavState(Optional.of(pageId), this.currentItemPage, history);
    }

    /** Returns a copy with the given item page index. */
    public @NotNull NavState withCurrentItemPage(int itemPage) {
        ConcurrentList<String> history = Concurrent.newList(this.pageHistory);
        return new NavState(this.currentPageId, itemPage, history);
    }

}
