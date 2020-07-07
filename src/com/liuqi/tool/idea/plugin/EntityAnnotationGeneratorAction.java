package com.liuqi.tool.idea.plugin;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.liuqi.tool.idea.plugin.utils.MyStringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 
 *
 * @author  LiuQi 2019/12/13-19:40
 * @version V1.0
 **/
public class EntityAnnotationGeneratorAction extends MyAnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        PsiClass aClass = this.getEditingClass(anActionEvent);

        // 如果已经被Entity注解了，不做任何操作
        if (null != aClass.getAnnotation("javax.persistence.Entity")) {
            return;
        }

        PsiAnnotation commentAnnotation = aClass.getAnnotation("com.liuqi.common.web.common.annotation.Comment");
        String tableComment = "";
        if (null != commentAnnotation) {
            String value = psiUtils.getAnnotationValue(commentAnnotation, "value")
                    .orElseGet(() -> psiUtils.getAnnotationValue(commentAnnotation, "entityName").orElse(""));
            tableComment = value.replace("\"", "");
        }
        String pTableComment = tableComment;

        // 增加Entity及Table注解
        String className = aClass.getName();
        assert className != null;
        if (className.endsWith("Entity")) {
            className = className.substring(0, className.length() - 6);
        }

        String tableName = "t_" + MyStringUtils.toUnderLineStr(className);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiAnnotation psiAnnotation = psiUtils.addAnnotation(aClass, "javax.persistence.Entity");
            psiUtils.addAnnotationFromStrAfter(aClass, "@Table(name = \"" + tableName + "\")", psiAnnotation);
            psiUtils.addAnnotationFromStrAfter(aClass, "\n" +
                    "@org.hibernate.annotations.Table(appliesTo = \"" + tableName + "\",comment = \"" + pTableComment + "\")", psiAnnotation);
            PsiJavaFile javaFile = (PsiJavaFile) aClass.getContainingFile();
            psiUtils.findClass("javax.persistence.Table").ifPresent(javaFile::importClass);
            psiUtils.findClass("javax.persistence.GeneratedValue").ifPresent(javaFile::importClass);
            psiUtils.findClass("javax.persistence.GenerationType").ifPresent(javaFile::importClass);

            // 为每一个属性增加注解
            for (PsiField field : aClass.getFields()) {
                String name = MyStringUtils.toUnderLineStr(field.getName());
                if ("id".equals(name)) {
                    PsiAnnotation psiAnnotation1 = psiUtils.addAnnotation(field, "javax.persistence.Id");
                    psiUtils.addAnnotationFromStrAfter(field, "@javax.persistence.GeneratedValue(strategy = GenerationType.IDENTITY)", psiAnnotation1);
                    continue;
                }

                PsiType psiType = field.getType();
                String typeName = psiType.getCanonicalText();
                String annotationField = "@javax.persistence.Column(name = \"" + name + "\", columnDefinition=\"";
                if (typeName.contains("String") || typeName.contains("Char")) {
                    annotationField = annotationField + "varchar(255) comment ''\")";
                } else if (typeName.contains("Float") || typeName.contains("float") || typeName.contains("Double") || typeName.contains("double")) {
                    annotationField = annotationField + "numeric(24, 4) comment ''\")";
                } else if (typeName.contains("LocalDateTime") || typeName.contains("Date")) {
                    if (typeName.toLowerCase().contains("update")) {
                        annotationField = annotationField + "timestamp not null default current_timestamp on update current_timestamp comment ''\")";
                    } else {
                        annotationField = annotationField + "timestamp not null default current_timestamp comment ''\")";
                    }
                } else if (typeName.contains("Long")) {
                    annotationField = annotationField + "bigint comment ''\")";
                } else if (psiType.getClass().isEnum() || typeName.toLowerCase().equals("boolean")) {
                    annotationField = annotationField + "int(1) default 0 comment ''\")";
                } else {
                    annotationField = annotationField + "integer comment ''\")";
                }

                psiUtils.addAnnotationFromStrFirst(field, annotationField);
            }
        });
    }
}
