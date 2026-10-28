package be.imgn.swing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisposalScopeTest {

    @Test
    void closesItsResourcesInReverseOrder() {
        DisposalScope scope = DisposalScope.root("test");
        List<String> closed = new ArrayList<>();
        scope.onClose(() -> closed.add("first"));
        scope.onClose(() -> closed.add("second"));

        scope.close();
        scope.close();

        assertThat(closed).containsExactly("second", "first");
        assertThat(scope.isClosed()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> scope.onClose(() -> {}));
    }

    @Test
    void closesItsChildrenWithIt() {
        DisposalScope parent = DisposalScope.root("parent");
        DisposalScope child = parent.child("child");
        List<String> closed = new ArrayList<>();
        parent.onClose(() -> closed.add("parent"));
        child.onClose(() -> closed.add("child"));

        parent.close();

        assertThat(closed).containsExactly("parent", "child");
        assertThat(child.isClosed()).isTrue();
        assertThat(child.parent()).containsSame(parent);
        assertThat(child.depth()).isEqualTo(1);
    }

    @Test
    void keepsClosingAfterAFailure() {
        DisposalScope scope = DisposalScope.root("test");
        List<String> closed = new ArrayList<>();
        scope.onClose(() -> closed.add("first"));
        scope.onClose(() -> {
            throw new IllegalStateException("expected");
        });

        scope.close();

        assertThat(closed).containsExactly("first");
    }

    @Test
    void withdrawsAResource() {
        DisposalScope scope = DisposalScope.root("test");
        List<String> closed = new ArrayList<>();
        Runnable withdraw = scope.register(() -> closed.add("withdrawn"));

        withdraw.run();
        scope.close();

        assertThat(closed).isEmpty();
    }
}
