import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * PSI操作辅助类
 *
 * @author LiuQi 2019/7/12-10:22
 * @version V1.0
 **/
public class PsiUtils {
    private Project project;

    private PsiUtils(Project project) {
        this.project = project;
    }

    public static PsiUtils of(Project project) {
        return new PsiUtils(project);
    }

    public void importClass(PsiClass srcClass, PsiClass... toImportClasses) {
        for (PsiClass toImportClass : toImportClasses) {
            ((PsiJavaFile) srcClass.getContainingFile()).importClass(toImportClass);
        }
    }

    public String getPackageName(PsiClass psiClass) {
        return ((PsiJavaFile)psiClass.getContainingFile()).getPackageName();
    }

    public Optional<String> getAnnotationValue(PsiClass psiClass, String annotation, String field) {
        return Optional.ofNullable(psiClass.getAnnotation(annotation)).map(a -> {
            PsiAnnotationMemberValue value = a.findAttributeValue(field);
            if (null != value) {
                return Optional.of(value.getText());
            } else {
                return Optional.<String>empty();
            }
        }).orElse(Optional.empty());
    }

    public Optional<String> getAnnotationValue(PsiFile psiFile, String annotation, String field) {
        return getAnnotationValue(((PsiJavaFile)psiFile).getClasses()[0], annotation, field);
    }

    public String getPackageAndName(PsiClass psiClass) {
        return ((PsiJavaFile)psiClass.getContainingFile()).getPackageName().concat(".").concat(psiClass.getName());
    }

    /**
     * 格式化代码
     *
     * @param psiElement 需要格式化的文件
     */
    public void format(PsiElement psiElement) {
        CodeStyleManager.getInstance(project).reformat(psiElement);
    }

    /**
     * 查找类
     *
     * @param className 类名
     * @return 查找到的类
     */
    public Optional<PsiClass> findClass(String className) {
        return findClass(className, psiClass -> true);
    }

    public Optional<PsiClass> findClass(String className, Predicate<PsiClass> predicate) {
        PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(project);

        int idx = className.lastIndexOf(".");
        if (-1 != idx) {
            String packageName = className.substring(0, idx);
            String name = className.substring(idx + 1);
            PsiClass[] classes = shortNamesCache.getClassesByName(name, GlobalSearchScope.allScope(project));

            if (0 != classes.length) {
                for (PsiClass aClass : classes) {
                    PsiJavaFile javaFile = (PsiJavaFile) aClass.getContainingFile();
                    if (javaFile.getPackageName().equals(packageName) && predicate.test(aClass)) {
                        return Optional.of(aClass);
                    }
                }
            }
        } else {
            PsiClass[] classes = shortNamesCache.getClassesByName(className, GlobalSearchScope.allScope(project));
            if (0 != classes.length) {
                for (PsiClass aClass : classes) {
                    if (predicate.test(aClass)) {
                        return Optional.ofNullable(aClass);
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 获取或者创建子目录
     *
     * @param parentDirectory  父级目录
     * @param subDirectoryName 子目录名称
     * @return 查找到的或者创建的子目录名称
     */
    public PsiDirectory getOrCreateSubDirectory(PsiDirectory parentDirectory, String subDirectoryName) {
        return Optional.ofNullable(parentDirectory.findSubdirectory(subDirectoryName)).orElseGet(() -> parentDirectory.createSubdirectory(subDirectoryName));
    }
}
