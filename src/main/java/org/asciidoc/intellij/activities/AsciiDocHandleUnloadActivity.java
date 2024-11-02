package org.asciidoc.intellij.activities;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.CannotUnloadPluginException;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.asciidoc.intellij.AsciiDocPlugin;
import org.asciidoc.intellij.AsciiDocWrapper;
import org.asciidoc.intellij.editor.AsciiDocSplitEditor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/**
 * This takes care of unloading the plugin on uninstalls or updates.
 * WARNING: A dynamic unload will usually only succeed when the application is NOT in debug mode;
 * classes might be marked as "JNI Global" due to this, and not reclaimed, and then unloading fails.
 */
public class AsciiDocHandleUnloadActivity implements StartupActivity, DumbAware, LightEditCompatible {

  private static final com.intellij.openapi.diagnostic.Logger LOG =
    com.intellij.openapi.diagnostic.Logger.getInstance(AsciiDocHandleUnloadActivity.class);

  private static boolean setupComplete;

  @Override
  public void runActivity(@NotNull Project project) {
    setupListener();
  }

  public static synchronized void setupListener() {
    if (!setupComplete) {
      setupComplete = true;
      LOG.info("setup of subscription");
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        // check application first to don't interfere with unit tests, as this is not initialized for Unit tests
        application.invokeLater(() -> {
          MessageBusConnection busConnection = application.getMessageBus().connect();
          Disposer.register(application, busConnection::disconnect);
          busConnection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
            @Override
            public void checkUnloadPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) throws CannotUnloadPluginException {
              if (Objects.equals(pluginDescriptor.getPluginId().getIdString(), AsciiDocPlugin.PLUGIN_ID)) {
                LOG.info("checkUnloadPlugin");
                // https://github.com/asciidoctor/asciidoctor-intellij-plugin/issues/512
                // another reason: on Windows even after unloading JAR file of the plugin still be locked and can't be deleted, making uninstall impossible
                // https://youtrack.jetbrains.com/issue/IDEA-244471
                // Update: IDEA-244471 might not be relevant here as the plugin will have the version number in the JAR file, therefore a change file will have a new name

                // before trying to re-enable this for internal mode, try to unload plugin in development mode and analyze heap dumps.
                // if (!ApplicationManager.getApplication().isInternal()) {
                   throw new CannotUnloadPluginException("unloading mechanism is not safe, incomplete unloading might lead to strange exceptions");
                // }
                // found that "IdeScriptEngineManagerImpl" will hold a reference to "org.jruby.embed.jsr223.JRubyEngineFactory"
                // https://youtrack.jetbrains.com/issue/IDEA-285933
                // AsciiDoc.checkUnloadPlugin();
              }
            }

            @Override
            public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
              if (Objects.equals(pluginDescriptor.getPluginId().getIdString(), AsciiDocPlugin.PLUGIN_ID)) {
                LOG.info("beforePluginUnload");
                AsciiDocWrapper.beforePluginUnload();
                for (Project project : ProjectManager.getInstance().getOpenProjects()) {

                  // Workaround for https://youtrack.jetbrains.com/issue/IJPL-18535/
                  try {
                    DaemonCodeAnalyzer dca = DaemonCodeAnalyzer.getInstance(project);
                    Field myUpdateProgress = DaemonCodeAnalyzerImpl.class.getDeclaredField("myUpdateProgress");
                    myUpdateProgress.setAccessible(true);
                    Map<FileEditor, DaemonProgressIndicator> map = (Map<FileEditor, DaemonProgressIndicator>) myUpdateProgress.get(dca);
                    map.entrySet().clear();
                  } catch (NoSuchFieldException | IllegalAccessException e) {
                    // nopp
                  }

                  // Possibly not necessary in the future if IntelliJ doesn't hold on to references
                  FileEditorManager fem = FileEditorManager.getInstance(project);
                  for (FileEditor editor : fem.getAllEditors()) {
                    if ((editor instanceof AsciiDocSplitEditor)) {
                      ApplicationManager.getApplication().runReadAction(() -> {
                        VirtualFile vFile = editor.getFile();
                        if (vFile != null && vFile.isValid()) {
                          // an AsciiDoc file in a non-split editor, close and re-open the file to enforce split editor
                          ApplicationManager.getApplication().runWriteAction(() -> {
                            // closing the file might trigger a save, therefore, wrap in write action
                            if (!project.isDisposed()) {
                              fem.closeFile(vFile);
                            }
                          });
                        }
                      });
                    }
                  }
                }
                busConnection.dispose();
              }
            }
          });
        });
      }
    }
  }

}
