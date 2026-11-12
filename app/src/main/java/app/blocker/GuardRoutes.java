package app.blocker;

import java.util.Set;

/** Usage events identify the foreground app. Android Settings hosts details for every app. */
final class GuardRoutes {
    private final Set<String> essential;

    GuardRoutes(Set<String> essential) {
        this.essential = essential;
    }

    boolean shouldBlock(String foregroundPackage, Set<String> selected) {
        return foregroundPackage != null && !essential.contains(foregroundPackage)
            && selected.contains(foregroundPackage);
    }
}
