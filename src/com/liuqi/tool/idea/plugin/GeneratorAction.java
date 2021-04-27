package com.liuqi.tool.idea.plugin;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.liuqi.tool.idea.plugin.bean.GeneratorConfig;
import com.liuqi.tool.idea.plugin.utils.MyStringUtils;
import com.liuqi.tool.idea.plugin.utils.PsiUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.psi.PsiType.BOOLEAN;

/**
 * 实体类代码创建器
 * 生成代码路径：
 * bean
 * - dto: DTO对象存储路径
 * - query：查询对象存储路径
 * - mapper：DTO与Entity对象转换器路径
 * domain
 * - dao：MyBatis数据库操作类存储路径
 * - repository：JPA数据库操作类存储路径
 * - entity：实体类存储路径
 * service：服务类存储路径
 * web：控制器类存储路径
 * <p>
 * 注意会使用两个公共包，源码地址：https://github.com/icarusliu/lcommon
 *
 * @author LiuQi 2019/7/11-10:50
 * @version V1.0
 **/
public class GeneratorAction extends MyAnAction {
    private PsiDirectory workDir;
    private final Map<String, PsiDirectory> directoryMap = new HashMap<>(16);
    private GeneratorConfig config;
    private final Comment comment = new Comment();
    private EntityClasses entityClasses;

    @Override
    public synchronized void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        PsiClass aClass = this.getEditingClass(anActionEvent);
        if (null == aClass) {
            return;
        }

        if (null == aClass.getAnnotation("javax.persistence.Entity")) {
            // 只处理被Entity注解的类
            return;
        }

        // 加载生成配置
        config = GeneratorConfig.load(project);

        // 获取当前实体所在目录的上两级目录，需要严格按说明中的目录组织，其它目录不考虑
        workDir = aClass.getContainingFile().getContainingDirectory().getParentDirectory().getParentDirectory();

        entityClasses = new EntityClasses();

        // 加载所有目录
        initDirs();
        entityClasses.setEntityClass(aClass);

        // 获取是否有deleted字段，如果有的话，生成的service方法、dao中的语句中都要增加相应的条件
        Optional.ofNullable(aClass.findFieldByName("deleted", false))
                .ifPresent(field -> config.setWithDeleted(true));

        // 获取是否有createTime字段，如果有的话，生成service方法的save方法,dao中的语句排序需要增加对应的排序
        Optional.ofNullable(aClass.findFieldByName("createTime", false))
                .ifPresent(field -> config.setWithCreateTime(true));

        Optional.ofNullable(aClass.findFieldByName("userId", false))
                .ifPresent(field -> config.setWithUserId(true));

        // 加载注释信息
        PsiAnnotation commentAnnotation = aClass.getAnnotation("com.liuqi.common.web.common.annotation.Comment");
        if (null != commentAnnotation) {
            String value = psiUtils.getAnnotationValue(commentAnnotation, "value")
                    .orElseGet(() -> psiUtils.getAnnotationValue(commentAnnotation, "entityName").orElse(""));
            comment.text = value.replace("\"", "");
            comment.author = psiUtils.getAnnotationValue(commentAnnotation, "author").orElse("EntityCodeGenerator")
                    .replace("\"", "");
        }

        // 在实体类所在包的同级的repository中创建Repository
        WriteCommandAction.runWriteCommandAction(project, this::createRepository);
    }

    /**
     * 根据目录清单初始化目录
     * 如果目录不存在则进行创建
     */
    private void initDirs() {
        List<String> directories = Arrays.asList("bean", "bean/dto", "bean/mapper", "bean/query",
                "domain", "domain/dao", "domain/entity", "domain/repository", "service",
                "web");

        directoryMap.clear();

        directories.forEach(dir -> {
            if (!dir.contains("/")) {
                PsiDirectory directory = workDir.findSubdirectory(dir);
                if (null == directory) {
                    directory = workDir.createSubdirectory(dir);
                }

                directoryMap.put(dir, directory);
            } else {
                String[] dirs = dir.split("/");
                PsiDirectory directory = workDir.findSubdirectory(dirs[0]);
                if (null == directory) {
                    directory = workDir.createSubdirectory(dirs[0]);
                }

                PsiDirectory subDir = directory.findSubdirectory(dirs[1]);
                if (null == subDir) {
                    subDir = directory.createSubdirectory(dirs[1]);
                }
                directoryMap.put(dirs[1], subDir);
            }
        });
    }

    /**
     * 创建Repository
     */
    private void createRepository() {
        String entityName = entityClasses.getEntityName();
        assert entityName != null;
        PsiDirectory repositoryDirectory = directoryMap.get("repository");

        String repositoryName = entityName.replace("Entity", "").concat("Repository");
        getBaseRepositoryClass(repositoryDirectory, baseRepositoryClass ->
                ClassCreator.of(module).init(repositoryName,
                        comment.getContent("JPA数据库操作类") +
                                "\npublic interface " + repositoryName + " extends BaseRepository<" + entityClasses.getEntityClassName() + "> {}")
                        .importClass(entityClasses.getEntityClass())
                        .importClass(baseRepositoryClass)
                        .addTo(repositoryDirectory)
                        .and(() -> this.createDtoClass("", entityClasses::setDtoClass))
                        .and(() -> this.createDtoClass("Update", entityClasses::setDtoUpdateClass))
                        .and((() -> this.createDtoClass("Add", entityClasses::setDtoAddClass)))
                        .and(this::addExcelAnnotations)
        );
    }

    private String getEntityName() {
        String className = entityClasses.getEntityClassName();
        assert className != null;

        return className.replace("Entity", "");
    }

    /**
     * 创建DTO对象
     */
    private void createDtoClass(String name, Consumer<PsiClass> callback) {
        String entityName = this.getEntityName();
        PsiDirectory dtoDirectory = directoryMap.get("dto");

        // 先检查是否存在AbstractBaseDTO对象，如果存在的话DTO对象需要继承自该对象
        String dtoContent = comment.getContent("对象") + "\n@Data\npublic class " + entityName + name + "DTO";
        dtoContent += "{}";

        // 过滤掉一些不必要的字段
        List<String> disposedFields = new ArrayList<>();
        if ("Add".equals(name) || "Update".equals(name)) {
            disposedFields.addAll(Arrays.asList("deleted", "createTime", "userId", "userName", "updateTime", "userPhoto"));
            if ("Add".equals(name)) {
                disposedFields.add("id");
            }
        }

        // 根据Entity对象创建DTO对象
        ClassCreator.of(module).init(entityName + name + "DTO", dtoContent)
                .copyFields(entityClasses.getEntityClass(), disposedFields)
                .importClass("lombok.Data")
                .addTo(dtoDirectory)
                .and(callback);
    }

    /**
     * 增加Excel注解
     */
    private void addExcelAnnotations() {
        // 如果Workbook存在并且ExcelUtils存在，则生成Excel上传下载功能
        if (config.getExcelFunc()) {
            // 给DTO类增加ExcelField注解
            PsiClass dtoClass = entityClasses.dtoClass;
            PsiField[] fields = dtoClass.getFields();
            for (PsiField field : fields) {
                PsiUtils.of(module)
                        .addAnnotation(field, "ExcelField");
            }

            PsiUtils.of(module)
                    .findClass("ExcelField")
                    .ifPresent(psiClass -> PsiUtils.of(module)
                            .importClass(dtoClass, psiClass));
        }

        createMapperClass();
    }

    /**
     * 创建Mapper对象
     */
    private void createMapperClass() {
        String entityName = entityClasses.getEntityName();

        // 增加mapper对象
        PsiDirectory mapperDirectory = directoryMap.get("mapper");

        // 先创建EntityMapper对象
        // 先检查EntityMapper是否存在
        Optional<PsiClass> entityMapperClassOptional = psiUtils.findClass("EntityMapper");
        Consumer<PsiClass> createMapperFunction = entityMapperClass -> {
            String mapperName = entityName + "Mapper";
            ClassCreator.of(module).init(mapperName,
                    comment.getContent("对象转换器") + "\n@Mapper(componentModel = \"spring\")" +
                            "public interface " + mapperName + " extends EntityMapper<"
                            + entityClasses.getDtoClass().getName() + ", " + entityClasses.getEntityClass().getName() + "> {}")
                    .importClass("org.mapstruct.Mapper")
                    .importClass(entityClasses.getEntityClass())
                    .addTo(mapperDirectory)
                    .and(mapperClass -> {
                        psiUtils.importClass(mapperClass, entityClasses.getDtoClass(), entityMapperClass);

                        // 先增加MyBatis的Dao对象及XML文件
                        entityClasses.setMapperClass(mapperClass);
                        createQuery();
                    });
        };

        if (entityMapperClassOptional.isPresent()) {
            // 创建Mapper对象
            createMapperFunction.accept(entityMapperClassOptional.get());
        } else {
            // 不存在时，先创建EntityMapper然后再创建Mapper
            ClassCreator.of(module).init("EntityMapper", "public interface EntityMapper<D, E> {\n" +
                    "    E toEntity(D dto);\n" +
                    "    D toDto(E entity);\n" +
                    "    List<E> toEntity(List<D> dtoList);\n" +
                    "    List <D> toDto(List<E> entityList);\n" +
                    "}")
                    .importClass("java.util.List")
                    .addTo(mapperDirectory)
                    .and(createMapperFunction);
        }

    }

    /**
     * 增加MyBatis相关文件
     */
    private void createQuery() {
        // 获取BaseQuery对象，没有就不使用
        boolean baseQueryExists = psiUtils.findClass("BaseQuery")
                .isPresent();

        StringBuilder content = new StringBuilder()
                .append(comment.getContent("查询对象"))
                .append("\n@Data")
                .append("\npublic class ")
                .append(entityClasses.getEntityName())
                .append("Query ");

        if (baseQueryExists) {
            content.append("extends BaseQuery{private List<Long> ids; private Long idNot;  }");
        } else {
            content.append("{private Integer page;  \nprivate Integer size; private List<Long> ids; private Long id; private Long idNot;   }");
        }

        // 先创建Query对象
        PsiDirectory queryDirectory = directoryMap.get("query");
        ClassCreator creator = ClassCreator.of(module)
                .init(entityClasses.getEntityName() + "Query", content.toString())
                .importClass("lombok.Data")
                .importClass("java.util.List");
        if (!baseQueryExists) {
            creator.addGetterAndSetterMethods();
        } else {
            creator.importClass("BaseQuery");
        }

        creator.addTo(queryDirectory)
                .and(queryClass -> {
                    psiUtils.addGetterAndSetterMethods(queryClass);
                    entityClasses.setQueryClass(queryClass);

                    // 在Repository的同级目录下创建dao目录及dao对象
                    createDao();
                });
    }

    /**
     * 添加Mybatis Dao
     */
    private void createDao() {
        PsiDirectory daoDirectory = directoryMap.get("dao");

        if (config.getWithSuper()) {
            ClassCreator.of(module).init(entityClasses.getEntityName() + "Dao",
                    comment.getContent("数据库操作类") +
                            "\n@Mapper public interface " + entityClasses.getEntityName() + "Dao extends " + config.getSuperDao() + "" +
                            "<" + entityClasses.getDtoClass().getName() + ">" +
                            "{}")
                    .importClass("org.apache.ibatis.annotations.Mapper")
                    .importClass(config.getSuperDao())
                    .addTo(daoDirectory)
                    .and(daoClass -> {
                        psiUtils.importClass(daoClass, entityClasses.getDtoClass());
                        entityClasses.setDaoClass(daoClass);
                        createDaoMappingFile();
                    });
        } else {
            ClassCreator.of(module).init(entityClasses.getEntityName() + "Dao",
                    comment.getContent("数据库操作类") +
                            "\n@Mapper public interface " + entityClasses.getEntityName() + "Dao {" +
                            "List<" + entityClasses.getDtoClass().getName() + "> query(" + entityClasses.getQueryClass().getName() + " query); " +
                            "void batchAdd(@Param(\"list\") List<" + entityClasses.getDtoClass().getName() + "> dataList);" +
                            "}")
                    .importClass("java.util.List")
                    .importClass("org.apache.ibatis.annotations.Mapper")
                    .importClass("org.apache.ibatis.annotations.Param")
                    .addTo(daoDirectory)
                    .and(daoClass -> {
                        psiUtils.importClass(daoClass, entityClasses.getQueryClass(), entityClasses.getDtoClass());
                        entityClasses.setDaoClass(daoClass);
                        createDaoMappingFile();
                    });
        }
    }

    /**
     * 创建MyBatis映射文件
     */
    private void createDaoMappingFile() {
        // 获取mappers目录，在resources目录下，如果没有这个目录，那么创建一个目录
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        List<VirtualFile> sourceRoots = rootManager.getSourceRoots(JavaModuleSourceRootTypes.RESOURCES);
        VirtualFile resourceDirectory = sourceRoots.get(0);
        VirtualFile mappersDirectory = resourceDirectory.findChild("mappers");
        if (null == mappersDirectory) {
            try {
                mappersDirectory = resourceDirectory.createChildDirectory(this, "mappers");
            } catch (IOException e) {
                e.printStackTrace();
                mappersDirectory = resourceDirectory;
            }
        }

        PsiDirectory psiDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(mappersDirectory);

        String fileName = entityClasses.getEntityName() + "Dao.xml";
        PsiFile psiFile = psiDirectory.findFile(fileName);
        if (null == psiFile) {

            String daoPackage = ((PsiJavaFile) entityClasses.getDaoClass().getContainingFile()).getPackageName();
            String dtoPackage = ((PsiJavaFile) entityClasses.getDtoClass().getContainingFile()).getPackageName();
            StringBuilder content = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                    "<!DOCTYPE mapper\n" +
                    "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                    "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">" +
                    "<mapper namespace=\"" + daoPackage + "." + entityClasses.getDaoClass().getName() + "\">");

            // 增加resultMap映射
            content.append("<resultMap id=\"resultMap\" type=\"").append(dtoPackage).append(".").append(entityClasses.getDtoClass().getName()).append("\">");
            PsiClass entityClass = entityClasses.getEntityClass();

            StringBuilder columns = new StringBuilder();
            StringBuilder insertColumns = new StringBuilder();
            StringBuilder insertFields = new StringBuilder();
            for (PsiField field : entityClass.getAllFields()) {
                String fieldName = field.getName();
                String str = Arrays.stream(Objects.requireNonNull(StringUtils.splitByCharacterTypeCamelCase(fieldName)))
                        .reduce((s1, s2) -> s1.toLowerCase().concat("_").concat(s2.toLowerCase())).orElse("");

                content.append("<result property=\"").append(fieldName).append("\" column=\"").append(str).append("\"");

                // 如果是枚举类
                String typeClassName = field.getType().getCanonicalText();
                psiUtils.findClass(typeClassName).ifPresent(typeClass -> {
                    if (typeClass.isEnum()) {
                        content.append(" typeHandler=\"org.apache.ibatis.type.EnumOrdinalTypeHandler\"");
                    }
                });

                content.append("/>");

                if (0 == columns.length()) {
                    columns.append("t1.").append(str);
                    insertColumns.append(str);
                    insertFields.append("#{item.").append(fieldName).append("}");
                } else {
                    columns.append(",").append("t1.").append(str);
                    insertColumns.append(",").append(str);
                    insertFields.append(",#{item.").append(fieldName).append("}");
                }
            }

            // 获取表名
            String tableName = "tableName";
            PsiAnnotation annotation = entityClass.getAnnotation("javax.persistence.Table");
            if (null != annotation) {
                PsiAnnotationMemberValue memberValue = annotation.findAttributeValue("name");
                if (null != memberValue) {
                    tableName = memberValue.getText();
                }
            }

            tableName = tableName.replaceAll("\"", "");

            content.append("</resultMap>\n\n")
                    .append("<sql id=\"columns\">\n")
                    .append(columns.toString())
                    .append("</sql>\n\n")
                    .append("<sql id=\"tables\">\n")
                    .append("\nfrom ").append(tableName).append(" t1\n")
                    .append("</sql>\n\n")
                    .append("<sql id=\"baseSelect\">\n")
                    .append("select \n<include refid=\"columns\"/>\n")
                    .append("<include refid=\"tables\"/>")
                    .append("\n</sql>\n\n");

            content.append("<sql id=\"conditions\">\n")
                    .append("<where>\n");
            if (config.getWithDeleted()) {
                content.append(" t1.deleted = 0\n");
            }

            // 增加id/idNot/keyword的查询条件
            content.append("<if test=\"null != id\">\n")
                    .append(" and t1.id = #{id}\n")
                    .append("</if>\n")
                    .append("<if test=\"null != idNot\">\n")
                    .append("and t1.id <![CDATA[<>]]> #{idNot}\n")
                    .append("</if>\n")
                    .append("<if test=\"null != ids\">\n")
                    .append("and t1.id in <foreach collection=\"ids\" item=\"item\" open=\"(\" close=\")\" separator=\",\">\n")
                    .append("#{item}\n")
                    .append("</foreach>\n")
                    .append("</if>\n")
                    .append("</where>\n</sql>\n\n");

            content.append("<select id=\"query\" parameterType=\"")
                    .append(psiUtils.getPackageAndName(entityClasses.getQueryClass()))
                    .append("\" resultMap=\"resultMap\">")
                    .append("<include refid=\"baseSelect\"/>\n")
                    .append("<include refid=\"conditions\"/>\n");

            content.append("\n <if test=\"null != orderByProperty and '' != orderByProperty\">\n order by t1.${orderByProperty} ${orderByType}\n</if>");

            if (config.getWithCreateTime()) {
                content.append("\n<if test=\"null == orderByProperty or '' == orderByProperty\"> \norder by t1.id desc \n</if>");
            }

            content.append("\n</select>\n\n");

            content.append("<select id=\"count\" parameterType=\"")
                    .append(psiUtils.getPackageAndName(entityClasses.getQueryClass()))
                    .append("\" resultType=\"long\">")
                    .append("select count(1) <include refid=\"tables\"/> \n")
                    .append("<include refid=\"conditions\"/>\n")
                    .append("\n</select>\n\n");

            content.append("\n<select id=\"findAll\" parameterType=\"")
                    .append(psiUtils.getPackageAndName(entityClasses.getQueryClass()))
                    .append("\" resultMap=\"resultMap\">")
                    .append("\n<include refid=\"baseSelect\"/>");

            if (config.getWithDeleted()) {
                content.append(" \nwhere t1.deleted = 0");
            }

            content.append(" \n<if test=\"null != orderByProperty and '' != orderByProperty\"> \norder by t1.${orderByProperty} #{orderByType}\n</if>");

            if (config.getWithCreateTime()) {
                content.append("\n<if test=\"null == orderByProperty or '' == orderByProperty\"> \norder by t1.create_time desc \n</if>");
            }

            content.append("\n</select>\n\n");

            // 增加批量新增语句
            content.append("\n<insert id=\"batchAdd\" parameterType=\"")
                    .append(psiUtils.getPackageAndName(entityClasses.getDtoClass()))
                    .append("\">")
                    .append("\ninsert into ")
                    .append(tableName)
                    .append("(")
                    .append(insertColumns.toString())
                    .append(") values <foreach collection=\"list\" item=\"item\" open=\"\" close=\"\" separator=\",\">\n")
                    .append("(")
                    .append(insertFields.toString())
                    .append(")")
                    .append("\n</foreach></insert>\n\n")
            ;

            content.append("</mapper>");

            psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, XMLLanguage.INSTANCE,
                    content);
            psiUtils.format(psiFile);
            psiDirectory.add(psiFile);
        }

        createServiceImpl();
    }

    /**
     * 创建服务实现类
     */
    private void createServiceImpl() {
        String serviceName = entityClasses.getEntityName().concat("Service");
        // 增加接口服务实现
        PsiDirectory serviceImplDirectory = directoryMap.get("service");

        StringBuilder content = new StringBuilder(comment.getContent("服务") + "\n@Service public class ")
                .append(serviceName);
        if (config.getWithSuper()) {
            content.append(" extends ")
                    .append(config.getSuperService().substring(config.getSuperService().lastIndexOf(".") + 1))
                    .append("<").append(entityClasses.getEntityClassName())
                    .append(",").append(entityClasses.getDtoClass().getName())
                    .append(",").append(entityClasses.getQueryClass().getName())
                    .append("> ");
        }

        content.append("{");

        if (!config.getWithSuper()) {
            PsiClass repositoryClass = entityClasses.getRepositoryClass();
            String saveAllMethod = "save";
            if (0 != repositoryClass.findMethodsByName("saveAll", true).length) {
                saveAllMethod = "saveAll";
            }

            String daoFieldName = StringUtils.uncapitalize(entityClasses.getDaoClass().getName());

            content.append("@Resource private ").append(entityClasses.getMapperClass().getName()).append(" mapper; \n")
                    .append("\n@Resource private ").append(entityClasses.getRepositoryClass().getName()).append(" repository; \n")
                    .append("\n@Resource private ").append(entityClasses.getDaoClass().getName()).append(" ").append(daoFieldName).append("; \n")
                    .append("\n@Override public BaseQuery createQuery() { return new ").append(entityClasses.getQueryClass().getName()).append("();}\n")
                    .append("\n @Transactional public void save(").append(entityClasses.getDtoClass().getName()).append(" dto) { repository.save(mapper.toEntity(dto));}")
                    .append("\n @Transactional  public void save(List<").append(entityClasses.getDtoClass().getName()).append("> dtos) { repository.").append(
                    saveAllMethod).append("(mapper.toEntity(dtos)); }")
                    .append("\n @Transactional  public void delete(Long id) { repository.delete(id); }")
                    .append("\n @Transactional(readOnly = true)  public Optional<").append(entityClasses.getDtoClass().getName()).append(
                    "> findOne(Long id) { return Optional.ofNullable(mapper.toDto(repository.findOne(id))); }")
                    .append("\n @Transactional(readOnly = true) public List<").append(entityClasses.getDtoClass().getName()).append(
                    "> findAll() { return mapper.toDto(repository.findAll()); }")
                    .append("\n @Transactional(readOnly = true) public List<").append(entityClasses.getDtoClass().getName()).append("> query(")
                    .append(entityClasses.getQueryClass().getName()).append(" query) { return ").append(daoFieldName).append(".query(query);}")
                    .append("\n @Transactional(readOnly = true) public PageInfo<").append(entityClasses.getDtoClass().getName()).append("> pageQuery(").append(
                    entityClasses.getQueryClass().getName()).append(" query) {")
                    .append("if (null != query.getSize() && null != query.getPage()) {PageHelper.startPage(query.getPage(), query.getSize()); }")
                    .append("return new PageInfo<>(").append(daoFieldName).append(".query(query));}");

            if (config.getExcelFunc()) {
                content.append("\nprivate List<ExcelColumn<").append(entityClasses.getDtoClass().getName())
                        .append(">> getExcelColumns(){return ExcelUtils.initColumnsFromClass(").append(entityClasses.dtoClass.getName()).append(".class); }")
                        .append("\n public Workbook downloadTemplate() { return ExcelUtils.createExcelGenerator(getExcelColumns()).getWorkbook();}")
                        .append("\n public void upload(MultipartFile file) {ExcelUtils.createExcelReader(file, getExcelColumns(), ")
                        .append(entityClasses.getDtoClass().getName()).append(".class).setErrorProcessor(sheet->{}).read(this::save); }")
                        .append("\n@Override public Workbook download(").append(entityClasses.getQueryClass().getName()).append(" query) {List<")
                        .append(entityClasses.getDtoClass().getName()).append("> dataList = query(query); return ExcelUtils.createExcelGenerator(getExcelColumns(), dataList).getWorkbook();} ");
            }
        } else {
            content.append("\n@Override public ")
                    .append(entityClasses.getQueryClass().getName())
                    .append(" createQuery() { return new ")
                    .append(entityClasses.getQueryClass().getName())
                    .append("();}\n");

            // 增加按用户删除、修改的接口
            if (config.getWithUserId()) {
                content.append("\npublic void update(Long userId, ")
                        .append(entityClasses.getDtoUpdateClass().getName())
                        .append(" update){ ")
                        .append(entityClasses.getDtoClass().getName()).append(" dto = this.findOne(update.getId()).orElseThrow(() -> BusinessException.create(\"对象不存在\"));")
                        .append("if (!dto.getUserId().equals(userId)) {throw BusinessException.create(\"权限不足\");}")
                        .append("LBeanUtils.copyNonNullProperties(update, dto); this.save(dto); }\n")
                        // 新增
                        .append("\npublic ").append(entityClasses.getDtoClass().getName()).append(" add(Long userId, ")
                        .append(entityClasses.getDtoAddClass().getName()).append(" addDto){")
                        .append(entityClasses.getDtoClass().getName()).append(" dto = new ").append(entityClasses.getDtoClass().getName())
                        .append("(); LBeanUtils.copyNonNullProperties(addDto, dto);  dto.setUserId(userId);  return this.save(dto); }\n")
                        // 删除
                        .append("\npublic void delete(Long userId, Long id){")
                        .append(entityClasses.getDtoClass().getName()).append(" dto = this.findOne(id).orElseThrow(() -> BusinessException.create(\"对象不存在\"));")
                        .append("if (!dto.getUserId().equals(userId)) {throw BusinessException.create(\"权限不足\");}")
                        .append("this.delete(id); }\n")
                ;
            } else {
                content.append("\npublic void update(")
                        .append(entityClasses.getDtoUpdateClass().getName())
                        .append(" update){ ")
                        .append(entityClasses.getDtoClass().getName()).append(" dto = this.findOne(update.getId()).orElseThrow(() -> BusinessException.create(\"对象不存在\"));")
                        .append("LBeanUtils.copyNonNullProperties(update, dto); this.save(dto); }\n")

                        // 新增
                        .append("\npublic ").append(entityClasses.getDtoClass().getName()).append(" add(")
                        .append(entityClasses.getDtoAddClass().getName()).append(" addDto){")
                        .append(entityClasses.getDtoClass().getName()).append(" dto = new ").append(entityClasses.getDtoClass().getName())
                        .append("(); LBeanUtils.copyNonNullProperties(addDto, dto); return this.save(dto); }\n")
                ;
            }

            // 删除方法使用逻辑删除
            if (config.getWithDeleted()) {
                content.append("@Override public void delete(Long id) {repository.findById(id).ifPresent(item -> {item.setDeleted(true); " +
                        "repository.save(item); }); }");

                content.append("@Override public ")
                        .append(entityClasses.dtoClass.getName())
                        .append(" save(")
                        .append(entityClasses.dtoClass.getName())
                        .append(" dto) {if (null == dto.getId()) { dto.setDeleted(false); ");

                if (config.getWithCreateTime()) {
                    content.append("dto.setCreateTime(LocalDateTime.now());dto.setUpdateTime(LocalDateTime.now()); ");
                }

                content.append(" } return super.save(dto); }");

            } else if (config.getWithCreateTime()) {
                content.append("@Override public ")
                        .append(entityClasses.dtoClass.getName())
                        .append(" save(")
                        .append(entityClasses.dtoClass.getName())
                        .append(" dto) {if (null == dto.getId()) { dto.setCreateTime(LocalDateTime.now()); }" +
                                " return super.save(dto); }");
            }
        }


        content.append("}");

        ClassCreator.of(module).init(serviceName, content.toString())
                .importClass(entityClasses.getEntityClass())
                .importClass("org.springframework.stereotype.Service")
                .importClassIf("java.time.LocalDateTime", () -> config.getWithCreateTime())
                .importClassIf(config.getSuperService(), () -> config.getWithSuper())
                .importClass("BusinessException")
                .importClass("LBeanUtils")
                .importClass(entityClasses.getDtoAddClass())
                .importClass(entityClasses.getDtoUpdateClass())
                .importClass("AbstractBaseEntityService")
                .importClassIf(serviceName, () -> config.getExcelFunc())
                .importClassIf("ExcelUtils", () -> config.getExcelFunc())
                .importClassIf("Workbook", () -> config.getExcelFunc())
                .importClassIf("ExcelColumn", () -> config.getExcelFunc())
                .importClassIf("MultipartFile", () -> config.getExcelFunc())
                .addTo(serviceImplDirectory)
                .and(implClass -> {
                    entityClasses.setServiceClass(implClass);

                    psiUtils.importClass(implClass, entityClasses.getServiceClass(),
                            entityClasses.getRepositoryClass(), entityClasses.getDtoClass(), entityClasses.getQueryClass(),
                            entityClasses.getQueryClass());

                    createController();
                });
    }

    /**
     * 创建控制器
     */
    private void createController() {
        // 在Service同目录下获取controller或者web目录
        PsiDirectory controllerDirectory = directoryMap.get("web");

        String prefix = config.getControllerPrefix();

        // 判断是否要加API的注解
        Optional<PsiClass> apiClass = psiUtils.findClass("io.swagger.annotations.Api");
        boolean useAPI = apiClass.isPresent();

        String suffix = "Controller";

        String entityName = entityClasses.getEntityName();
        String controllerPath = getControllerPath(entityName);
        controllerPath = controllerPath.substring(0, 1).toLowerCase() + controllerPath.substring(1);

        entityClasses.controllerPath = prefix + "/" + controllerPath;

        StringBuilder content = new StringBuilder();
        content.append(comment.getContent("控制器"))
                .append("\n@RequestMapping(\"")
                .append(prefix)
                .append("/")
                .append(controllerPath)
                .append("\")")
                .append("@RestController");

        if (useAPI) {
            content.append("@Api(tags = \"")
                    .append(comment.text)
                    .append("控制器\")");
        }

        content.append(" public class ")
                .append(entityName)
                .append(suffix)
        .append("{");

//        if (config.getWithSuper()) {
//            content.append(" extends ")
//                    .append(config.getSuperController())
//                    .append("<").append(entityClasses.getDtoClass().getName())
//                    .append(",").append(entityClasses.getQueryClass().getName())
//                    .append(",").append(entityClasses.getServiceImplClass().getName())
//                    .append("> {");
//        }

//        if (!config.getWithSuper()) {
        String entityFieldName = MyStringUtils.firstLetterToLower(entityName);
        String entityServiceName = entityFieldName + "Service";

        content.append("@Resource private ")
                .append(entityClasses.getServiceClass().getName())
                .append(" ")
                .append(entityFieldName)
                .append("Service; ");

        // 新增方法
        content
                .append("@ApiOperation(\"新增\") @PostMapping(\"/add\")")
                .append("public void add(@RequestBody  ").append(entityClasses.getDtoAddClass().getName()).append(" ").append(entityFieldName).append(") { ");
        if (config.getWithUserId()) {
            content.append("AuthUser user = SecurityUtils.getLoginUser().orElseThrow(LogoutException::new);  ")
                    .append(entityFieldName)
                    .append("Service.add(user.getId(), ").append(entityFieldName).append(");}\n");
        } else {
            content.append(entityServiceName).append(".add(").append(entityFieldName).append("); }\n");
        }

        // 修改
        content
                .append("@ApiOperation(\"修改\") @PostMapping(\"/update\")")
                .append("public void update(@RequestBody  ").append(entityClasses.getDtoUpdateClass().getName()).append(" ").append(entityFieldName).append(") { ");
        if (config.getWithUserId()) {
            content.append("AuthUser user = SecurityUtils.getLoginUser().orElseThrow(LogoutException::new);  ")
                    .append(entityServiceName)
                    .append(".update(user.getId(), ").append(entityFieldName).append(");}\n");
        } else {
            content.append(entityServiceName).append(".update(").append(entityFieldName).append("); }\n");
        }

        // 删除
        content
                .append("@ApiOperation(\"根据主键删除\")  @DeleteMapping(\"/delete/{id}\") public void delete(@PathVariable(\"id\") Long id) {");
        if (config.getWithUserId()) {
            content.append("AuthUser user = SecurityUtils.getLoginUser().orElseThrow(LogoutException::new);  ")
                    .append(entityServiceName).append(".delete(user.getId(), id);}");
        } else {
            content.append(entityServiceName).append(".delete(id);}");
        }

        // 查询
        content.append("@ApiOperation(\"分页查询\") @PostMapping(\"/page-query\") public PageInfo<").append(entityClasses.getDtoClass().getName()).append(
                "> pageQuery(@RequestBody ")
                .append(entityClasses.getQueryClass().getName()).append(" query) { return ").append(entityFieldName).append(
                "Service.pageQuery(query);}");

        content
                .append("@ApiOperation(\"查询记录数\") @PostMapping(\"/count\") public Long count(@RequestBody ")
                .append(entityClasses.getQueryClass().getName()).append(" query) { return ").append(entityFieldName).append(
                "Service.count(query);}");

        if (config.getExcelFunc()) {
            content.append("@ApiOperation(\"模板下载\") @GetMapping(\"/template-download\") public void " +
                    "downloadTemplate(HttpServletResponse response) {ExcelUtils.writeExcelToResponse(").append(entityServiceName).append(".downloadTemplate(), response, \"template.xlsx\"); }")
                    .append("@ApiOperation(\"数据上传\") @PostMapping(\"/upload\") public void upload(@RequestParam(\"file\")MultipartFile file) {").append(entityServiceName).append(".upload(file);}")
                    .append("@ApiOperation(\"数据下载\") @PostMapping(\"/download\") public void download(@RequestBody ")
                    .append(entityClasses.getQueryClass().getName()).append(" query, HttpServletResponse response) { ExcelUtils.writeExcelToResponse(").append(entityServiceName).append(".download(query), response, \"data.xlsx\"); }");
        }
//        }

        content.append("}");

        // 在controller目录下创建Controller
        ClassCreator.of(module)
                .init(entityClasses.getEntityName() + suffix, content.toString())
                .importClass("javax.annotation.Resource")
                .importClass("org.springframework.web.bind.annotation.RequestMaping")
                .importClass("org.springframework.web.bind.annotation.PostMapping")
                .importClass("GetMapping")
                .importClass("DeleteMapping")
                .importClass("RequestBody")
                .importClass("io.swagger.annotations.Api")
                .importClass("io.swagger.annotations.ApiOperation")
                .importClass("PathVariable")
                .importClass("RequestParam")
                .importClass(entityClasses.getDtoAddClass())
                .importClass(entityClasses.getDtoUpdateClass())
                .importClassIf("AuthUser", config::getWithUserId)
                .importClassIf("SecurityUtils", config::getWithUserId)
                .importClassIf("LogoutException", config::getWithUserId)
                .importClass("com.github.pagehelper.PageInfo")
                .importClassIf(entityClasses.getServiceClass().getName(), () -> config.getWithSuper())
                .importClassIf("HttpServletResponse", () -> config.getExcelFunc())
                .importClassIf("ExcelUtils", () -> config.getExcelFunc())
                .importClassIf("MultipartFile", () -> config.getExcelFunc())
                .addTo(controllerDirectory)
                .and(controllerClass -> {
                    psiUtils.importClass(controllerClass, entityClasses.getDtoClass(), entityClasses.getServiceClass(),
                            entityClasses.getQueryClass());

                    // 创建前端页面
                    createPage();
                });
    }

    private String getControllerPath(String entityName) {
        return Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(entityName))
                .map(String::toLowerCase)
                .reduce((s1, s2) -> s1.concat("-").concat(s2))
                .orElse("");
    }

    /**
     * 创建前端管理页面
     */
    private void createPage() {
        if (config.getWithPage()) {
            String url = entityClasses.controllerPath;

            // 前端页面使用entityDataTable
            String mainClass = getControllerPath(entityClasses.getEntityName());
            StringBuilder content = new StringBuilder("<template>\n" +
                    "<!--" + comment.text + "管理-->\n" +
                    "    <div class='" + mainClass + "'>\n" +
                    "        <entity-data-table\n" +
                    "            :additionalQueryParams=\"queryParams\"\n" +
                    "            :urlPrefix=\"urlPrefix\"\n" +
                    "            :queryFlag=\"queryFlag\"\n" +
                    "            :columns=\"tableColumns\"\n" +
                    "            :lazyLoad=\"true\"\n" +
                    "        >\n" +
                    "            <template slot=\"searchBar\">\n" +
                    "            </template>\n" +
                    "        </entity-data-table>\n" +
                    "    </div>\n" +
                    "</template>\n" +
                    "\n" +
                    "<script>\n" +
                    "import entityDataTable from \"../../components/EntityDataTable\";\n" +
                    "\n" +
                    "export default {\n" +
                    "    name: \"App\",\n" +
                    "    watch: {},\n" +
                    "    components: { entityDataTable },\n" +
                    "    data() {\n" +
                    "        return {\n" +
                    "            urlPrefix: \"" + url + "\",\n" +
                    "\n" +
                    "            // 表格列信息\n" +
                    "            tableColumns: [\n" +
                    "                { field: \"id\", title: \"编号\", width: \"60px\", needAdd: false },\n");

            // 补充字段信息
            for (PsiField field : entityClasses.getEntityClass().getFields()) {
                if (field.getName().equals("id")) {
                    continue;
                }

                content.append("                {\n                    field: \"").append(field.getName()).append("\",\n");

                PsiType type = field.getType();
                String tableType = "text";
                if (type.equals(BOOLEAN)) {
                    tableType = "checkbox";
                } else if (type.equals(PsiType.INT) || type.equals(PsiType.LONG)) {
                    tableType = "number";
                }

                content.append("                    type: \"").append(tableType).append("\", \n");

                // 通过column注释获取字段的中文名称
                String title = "";
                PsiAnnotation annotation = field.getAnnotation("java.persistence.Column");
                if (null != annotation) {
                    PsiAnnotationMemberValue memberValue = annotation.findAttributeValue("columnDefinition");
                    if (null != memberValue) {
                        String columnDefinition = memberValue.getText();
                        int start = columnDefinition.indexOf("'");
                        int end = columnDefinition.lastIndexOf("'");
                        if (-1 != start && -1 != end) {
                            title = columnDefinition.substring(start + 1, end);
                        }
                    }
                }

                content.append(
                        "                    title: \"" + title + "\",\n" +
                                "                    width: \"120px\",\n" +
                                "                    required: true,\n" +
                                "                    editable: true,\n" +
                                "                    needAdd: true,\n" +
                                "                    options: [],\n" +
                                "                    dialogType: \"text\",\n" +
                                "                },\n");
            }

            content.append(
                    "                {\n" +
                            "                    field: \"operations\",\n" +
                            "                    type: \"operations\",\n" +
                            "                    title: \"操作\",\n" +
                            "                    width: \"120px\"\n" +
                            "                }\n" +
                            "            ],\n" +
                            "\n" +
                            "            queryParams: {\n" +
                            "            },\n" +
                            "            queryFlag: 0\n" +
                            "        };\n" +
                            "    },\n" +
                            "\n" +
                            "    mounted() {\n" +
                            "        this.queryFlag++;\n" +
                            "    },\n" +
                            "\n" +
                            "    methods: {}\n" +
                            "};\n" +
                            "</script>\n" +
                            "\n" +
                            "<style lang=\"scss\">\n" +
                            "." + mainClass + "{\n}" +
                            "</style>\n" +
                            "\n");

            psiUtils.createResourceFile("pages", entityClasses.getEntityName() + ".vue", content.toString());
        }
    }

    /**
     * 获取BaseRepository，如果没有这个类则创建一个
     */
    private void getBaseRepositoryClass(
            PsiDirectory repositoryDiresctory,
            Consumer<PsiClass> consumer) {
        // 获取BaseRepository，如果BaseRepository为空，则创建一个BaseRepository
        Optional<PsiClass> baseRepositoryClassOptional = psiUtils.findClass("BaseRepository");
        if (baseRepositoryClassOptional.isPresent()) {
            // 如果已经有了，则直接使用这个作为父类
            consumer.accept(baseRepositoryClassOptional.get());
            return;
        }

        // 如果没找到BaseRepository，则创建一个
        ClassCreator.of(module).init("BaseRepository",
                "@NoRepositoryBean public interface BaseRepository<E> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {}")
                .importClass("NoRepositoryBean")
                .importClass("JpaRepository")
                .importClass("JpaSpecificationExecutor")
                .addTo(repositoryDiresctory)
                .and(consumer);
    }

    private static class EntityClasses {
        private PsiClass entityClass;
        private PsiClass repositoryClass;
        private PsiClass mapperClass;
        private PsiClass dtoClass;
        private PsiClass dtoAddClass;
        private PsiClass dtoUpdateClass;
        private PsiClass serviceClass;
        private PsiClass controllerClass;
        private PsiClass queryClass;
        private PsiClass daoClass;
        private String controllerPath;

        PsiClass getEntityClass() {
            return entityClass;
        }

        EntityClasses setEntityClass(PsiClass entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        PsiClass getRepositoryClass() {
            return repositoryClass;
        }

        EntityClasses setRepositoryClass(PsiClass repositoryClass) {
            this.repositoryClass = repositoryClass;
            return this;
        }

        PsiClass getMapperClass() {
            return mapperClass;
        }

        EntityClasses setMapperClass(PsiClass mapperClass) {
            this.mapperClass = mapperClass;
            return this;
        }

        PsiClass getDtoClass() {
            return dtoClass;
        }

        EntityClasses setDtoClass(PsiClass dtoClass) {
            this.dtoClass = dtoClass;
            return this;
        }

        public PsiClass getServiceClass() {
            return serviceClass;
        }

        public EntityClasses setServiceClass(PsiClass serviceClass) {
            this.serviceClass = serviceClass;
            return this;
        }

        public PsiClass getControllerClass() {
            return controllerClass;
        }

        public EntityClasses setControllerClass(PsiClass controllerClass) {
            this.controllerClass = controllerClass;
            return this;
        }


        String getEntityName() {
            return Objects.requireNonNull(this.getEntityClass().getName()).replace("Entity", "");
        }

        String getEntityClassName() {
            return this.entityClass.getName();
        }

        PsiClass getQueryClass() {
            return queryClass;
        }

        EntityClasses setQueryClass(PsiClass queryClass) {
            this.queryClass = queryClass;
            return this;
        }

        PsiClass getDaoClass() {
            return daoClass;
        }

        EntityClasses setDaoClass(PsiClass daoClass) {
            this.daoClass = daoClass;
            return this;
        }

        public PsiClass getDtoAddClass() {
            return dtoAddClass;
        }

        public void setDtoAddClass(PsiClass dtoAddClass) {
            this.dtoAddClass = dtoAddClass;
        }

        public PsiClass getDtoUpdateClass() {
            return dtoUpdateClass;
        }

        public void setDtoUpdateClass(PsiClass dtoUpdateClass) {
            this.dtoUpdateClass = dtoUpdateClass;
        }
    }

    private static final class Comment {
        private String text;
        private String author;

        private String getContent(String cName) {
            return "/** " + text + cName + " \n * @author " + author
                    + " " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " **/";
        }
    }

    public static void main(String[] args) {
        System.out.println("test\"".replace("\"", ""));
    }
}
