/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.vladsch.idea.multimarkdown.language;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import com.vladsch.idea.multimarkdown.MultiMarkdownIcons;
import com.vladsch.idea.multimarkdown.MultiMarkdownLanguage;
import com.vladsch.idea.multimarkdown.MultiMarkdownPlugin;
import com.vladsch.idea.multimarkdown.MultiMarkdownProjectComponent;
import com.vladsch.idea.multimarkdown.psi.*;
import com.vladsch.idea.multimarkdown.psi.impl.MultiMarkdownPsiImplUtil;
import com.vladsch.idea.multimarkdown.spellchecking.SuggestionList;
import com.vladsch.idea.multimarkdown.util.FileReference;
import com.vladsch.idea.multimarkdown.util.FileReferenceLink;
import com.vladsch.idea.multimarkdown.util.FileReferenceList;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.vladsch.idea.multimarkdown.psi.MultiMarkdownTypes.WIKI_LINK_REF;
import static com.vladsch.idea.multimarkdown.psi.MultiMarkdownTypes.WIKI_LINK_TITLE;

public class MultiMarkdownCompletionContributor extends CompletionContributor {
    private static final Logger logger = Logger.getLogger(MultiMarkdownCompletionContributor.class);

    public MultiMarkdownCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PsiElement.class).withLanguage(MultiMarkdownLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet resultSet) {
                        PsiElement element = parameters.getPosition();
                        int offset = parameters.getOffset();
                        //logger.info("Completion for " + element + " at pos " + String.valueOf(offset));

                        IElementType elementType = element.getNode().getElementType();

                        if (elementType == WIKI_LINK_TITLE) {
                            PsiElement parent = element.getParent();
                            while (parent != null && !(parent instanceof MultiMarkdownWikiLink) && !(parent instanceof MultiMarkdownFile)) {
                                parent = parent.getParent();
                            }

                            if (parent != null && parent instanceof MultiMarkdownWikiLink) {
                                SuggestionList suggestionList = ElementNameSuggestionProvider.getWikiPageTitleSuggestions(parent);

                                if (suggestionList.size() > 0) {
                                    for (String suggestion : suggestionList.asList()) {
                                        resultSet.addElement(LookupElementBuilder.create(suggestion)
                                                .withCaseSensitivity(true)
                                        );
                                    }
                                }
                            }
                        } else if (elementType == WIKI_LINK_REF) {
                            Document document = parameters.getEditor().getDocument();
                            VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);

                            if (virtualFile != null) {
                                Project fileProject = parameters.getEditor().getProject();
                                if (fileProject != null) {
                                    MultiMarkdownProjectComponent projectComponent = MultiMarkdownPlugin.getProjectComponent(fileProject);
                                    FileReferenceList wikiFileReferenceList = projectComponent.getFileReferenceList().query()
                                            .inSource(virtualFile, fileProject)
                                            .spaceDashEqual()
                                            .allWikiPageRefs();

                                    for (FileReference fileReference : wikiFileReferenceList.getFileReferences()) {
                                        addWikiPageRefCompletion(resultSet, (FileReferenceLink) fileReference, true);
                                    }

                                    for (FileReference fileReference : wikiFileReferenceList.getFileReferences()) {
                                        addWikiPageRefCompletion(resultSet, (FileReferenceLink) fileReference, false);
                                    }
                                }
                            }
                        }
                    }
                }
        );
    }

    protected void addWikiPageRefCompletion(@NotNull CompletionResultSet resultSet, FileReferenceLink fileReference, boolean accessible) {
        String wikiPageRef = fileReference.getWikiPageRef();
        boolean isWikiPageAccessible = fileReference.isWikiAccessible();

        if (accessible == isWikiPageAccessible) {
            if (isWikiPageAccessible || fileReference.getUpDirectories() == 0) {
                //String wikiPageShortRef = toFile.getWikiPageRef(null, WANT_WIKI_REF | ALLOW_INACCESSIBLE_WIKI_REF);
                String linkRefFileName = fileReference.getLinkRef();

                //logger.info("Adding " + wikiPageRef + " to completions");
                LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(wikiPageRef)
                        //.withLookupString(wikiPageShortRef)
                        .withCaseSensitivity(true)
                        .withIcon(accessible && fileReference.isWikiPage() ? MultiMarkdownIcons.WIKI : MultiMarkdownIcons.FILE)
                        .withTypeText(linkRefFileName, false);

                if (!isWikiPageAccessible) {
                    // TODO: get the color from color settings
                    lookupElementBuilder = lookupElementBuilder
                            .withItemTextForeground(Color.RED);
                }

                resultSet.addElement(lookupElementBuilder);
            }
        }
    }
}
