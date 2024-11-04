package org.asciidoc.intellij.activities;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.JavaCoroutines;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.asciidoc.intellij.AsciiDocBundle;
import org.asciidoc.intellij.AsciiDocPlugin;
import org.asciidoc.intellij.settings.AsciiDocApplicationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows update notification.
 */
public class AsciiDocPluginUpdateActivity implements ProjectActivity, DumbAware, LightEditCompatible {

  @Override
  public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
    return JavaCoroutines.suspendJava(jc -> {
      final AsciiDocApplicationSettings settings = AsciiDocApplicationSettings.getInstance();
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(AsciiDocPlugin.PLUGIN_ID));
      if (plugin != null) {
        String version = plugin.getVersion();
        String oldVersion = settings.getVersion();
        boolean updated = !version.equals(oldVersion);
        if (updated) {
          settings.setVersion(version);

          // collect the recent changes the user hasn't seen yet
          StringBuilder changes = new StringBuilder();
          Matcher matcher = Pattern.compile("(?ms)<h3[^>]*>(?<version>[0-9.]+).*?</div>").matcher(plugin.getChangeNotes());
          int count = 0;
          while (matcher.find()) {
            if (matcher.group("version").equals(oldVersion)) {
              break;
            }
            count++;
            if (count > 5) {
              break;
            }
            changes.append(matcher.group());
          }
          // during hot-install, startup activity runs while notification groups are registered in write thread concurrently
          // therefore trigger notification in a read action that waits until the registration is complete
          ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runReadAction(() -> {
            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("asciidoctor-update");
            if (group != null) {
              // might happen on initial installation of the plugin at runtime
              Notification notification = group.createNotification(
                  AsciiDocBundle.message("asciidocUpdateNotification.title", version),
                  AsciiDocBundle.message("asciidocUpdateNotification.content") +
                    changes.toString()
                      // simplify HTML as not all tags are shown in event log
                      .replaceAll("<[/]?(div|h3|p)[^>]*>", "")
                      // avoid too many new lines as they will show as new lines in event log
                      .replaceAll("(?ms)<ul>\\s*", "<ul>")
                      // remove trailing blanks and empty lines
                      .replaceAll("(?ms)\\n[\\s]+", "\n"),
                  NotificationType.INFORMATION)
                .setListener(new NotificationListener.UrlOpeningListener(false));
              Notifications.Bus.notify(notification, project);
            }
          }));
        }
        jc.resume(Unit.INSTANCE);
      }
    }, continuation);
  }
}
