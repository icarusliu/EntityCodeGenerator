package com.liuqi.tool.idea.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.psi.*;
import com.liuqi.tool.idea.plugin.utils.PsiUtils;

/**
 * 
 *
 * @author  LiuQi 2019/12/13-19:44
 * @version V1.0
 **/
public abstract class MyAnAction extends AnAction {
    protected Project project;
    protected PsiDirectory containerDirectory;
    protected PsiUtils psiUtils;
    protected Module module;

    public PsiClass getEditingClass(AnActionEvent anActionEvent) {
        project = anActionEvent.getProject();

        if (null == project) {
            return null;
        }

        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        if (null == editor) {
            return null;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(anActionEvent.getProject()).getPsiFile(editor.getDocument());
        if (!(psiFile instanceof PsiJavaFile)) {
            return null;
        }

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();
        if (0 == classes.length) {
            return null;
        }

        containerDirectory = classes[0].getContainingFile().getContainingDirectory();
        module = FileIndexFacade.getInstance(project).getModuleForFile(classes[0].getContainingFile().getVirtualFile());
        psiUtils = PsiUtils.of(module);

        return classes[0];
    }
}
