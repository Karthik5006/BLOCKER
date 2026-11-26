package app.blocker;

import java.util.Set;

/** Essential packages stay launchable even if a saved selection includes them. */
final class GuardRoutes {
    private final Set<String> essential;

    GuardRoutes(Set<String> essential) {
        this.essential = essential;
    }

    boolean shouldBlock(String packageName, Set<String> selected) {
        return packageName != null && !essential.contains(packageName)
            && selected.contains(packageName);
    }
}
