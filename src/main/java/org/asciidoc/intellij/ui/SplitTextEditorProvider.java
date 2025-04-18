package org.asciidoc.intellij.ui;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class SplitTextEditorProvider implements FileEditorProvider, DumbAware {

  private static final String FIRST_EDITOR = "first_editor";
  private static final String SECOND_EDITOR = "second_editor";
  private static final String SPLIT_LAYOUT = "split_layout";

  @NotNull
  private final FileEditorProvider myFirstProvider;
  @NotNull
  private final FileEditorProvider mySecondProvider;

  @NotNull
  private final String myEditorTypeId;

  protected SplitTextEditorProvider(@NotNull FileEditorProvider firstProvider, @NotNull FileEditorProvider secondProvider) {
    myFirstProvider = firstProvider;
    mySecondProvider = secondProvider;

    myEditorTypeId = "split-provider[" + myFirstProvider.getEditorTypeId() + ";" + mySecondProvider.getEditorTypeId() + "]";
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return myFirstProvider.accept(project, file) && mySecondProvider.accept(project, file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    // called from create file from template
    FileEditor first = myFirstProvider.createEditor(project, file);
    FileEditor second = mySecondProvider.createEditor(project, file);
    return createSplitEditor(first, second);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return myEditorTypeId;
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    Element child = sourceElement.getChild(FIRST_EDITOR);
    FileEditorState firstState = null;
    if (child != null) {
      firstState = myFirstProvider.readState(child, project, file);
    }
    child = sourceElement.getChild(SECOND_EDITOR);
    FileEditorState secondState = null;
    if (child != null) {
      secondState = mySecondProvider.readState(child, project, file);
    }

    final Attribute attribute = sourceElement.getAttribute(SPLIT_LAYOUT);

    final String layoutName;
    if (attribute != null) {
      layoutName = attribute.getValue();
    } else {
      layoutName = null;
    }

    return new SplitFileEditor.MyFileEditorState(layoutName, firstState, secondState);
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    if (!(state instanceof SplitFileEditor.MyFileEditorState compositeState)) {
      return;
    }

    Element child = new Element(FIRST_EDITOR);
    if (compositeState.getFirstState() != null) {
      myFirstProvider.writeState(compositeState.getFirstState(), project, child);
      targetElement.addContent(child);
    }

    child = new Element(SECOND_EDITOR);
    if (compositeState.getSecondState() != null) {
      mySecondProvider.writeState(compositeState.getSecondState(), project, child);
      targetElement.addContent(child);
    }

    if (compositeState.getSplitLayout() != null) {
      targetElement.setAttribute(SPLIT_LAYOUT, compositeState.getSplitLayout());
    }
  }

  protected abstract FileEditor createSplitEditor(@NotNull FileEditor firstEditor, @NotNull FileEditor secondEditor);

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

}
