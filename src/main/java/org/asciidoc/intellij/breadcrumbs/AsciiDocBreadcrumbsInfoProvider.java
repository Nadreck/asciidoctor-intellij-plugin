package org.asciidoc.intellij.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.asciidoc.intellij.AsciiDocLanguage;
import org.asciidoc.intellij.psi.AbstractAsciiDocCodeBlock;
import org.asciidoc.intellij.psi.AsciiDocBlock;
import org.asciidoc.intellij.psi.AsciiDocSection;
import org.asciidoc.intellij.psi.AsciiDocSelfDescribe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsciiDocBreadcrumbsInfoProvider implements BreadcrumbsProvider {
  private static final Language[] OUR_LANGUAGES = {AsciiDocLanguage.INSTANCE};

  @Override
  public Language[] getLanguages() {
    return OUR_LANGUAGES;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof AsciiDocSection || e instanceof AsciiDocBlock;
  }


  @Nullable
  @Override
  public Icon getElementIcon(@NotNull PsiElement element) {
    return element.getIcon(0);
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    String title = switch (e) {
      case AsciiDocSection asciiDocSection -> asciiDocSection.getTitle();
      case AbstractAsciiDocCodeBlock abstractAsciiDocCodeBlock -> switch (abstractAsciiDocCodeBlock.getTitle()) {
        case String s -> s;
        case null -> "(" + abstractAsciiDocCodeBlock.getDefaultTitle() + ")";
      };
      case AsciiDocSelfDescribe asciiDocSelfDescribe -> asciiDocSelfDescribe.getFoldedSummary();
      default -> "";
    };
    return StringUtil.shortenTextWithEllipsis(title, 30, 5);
  }

}
