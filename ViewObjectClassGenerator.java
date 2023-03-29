package org.mybatis.generator.codegen.mybatis3.vo;

import cn.hutool.core.collection.CollectionUtil;
import com.vgosoft.core.constant.GlobalConstant;
import com.vgosoft.core.constant.enums.EntityAbstractParentEnum;
import com.vgosoft.core.constant.enums.ViewActionColumnEnum;
import com.vgosoft.core.constant.enums.ViewIndexColumnEnum;
import com.vgosoft.core.db.util.JDBCUtil;
import com.vgosoft.mybatis.generate.GenerateSqlTemplate;
import com.vgosoft.mybatis.sqlbuilder.InsertSqlBuilder;
import com.vgosoft.tool.core.VMD5Util;
import com.vgosoft.tool.core.VStringUtil;
import org.mybatis.generator.api.*;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.AbstractJavaGenerator;
import org.mybatis.generator.config.*;
import org.mybatis.generator.custom.ConstantsUtil;
import org.mybatis.generator.custom.ModelClassTypeEnum;
import org.mybatis.generator.custom.RelationTypeEnum;
import org.mybatis.generator.custom.ScalableElementEnum;
import org.mybatis.generator.custom.annotations.*;
import org.mybatis.generator.custom.pojo.RelationGeneratorConfiguration;
import org.mybatis.generator.internal.util.JavaBeansUtil;
import org.mybatis.generator.internal.util.StringUtility;
import org.mybatis.generator.internal.util.VoGenService;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mybatis.generator.internal.util.JavaBeansUtil.*;
import static org.mybatis.generator.internal.util.StringUtility.packageToDir;
import static org.mybatis.generator.internal.util.StringUtility.stringHasValue;
import static org.mybatis.generator.internal.util.messages.Messages.getString;

/**
 * 生成VO抽象父类
 *
 * @author <a href="mailto:TechCenter@vgosoft.com">vgosoft</a>
 * 2022-07-05 03:29
 * @version 3.0
 */
public class ViewObjectClassGenerator extends AbstractJavaGenerator {

    public static final String subPackageVo = "vo";
    public static final String subPackageMaps = "maps";
    public static final String subPackageAbs = "abs";

    TableConfiguration tc;
    private CommentGenerator commentGenerator;
    private String baseTargetPackage;
    private String abstractName;
    private String voType;
    private TopLevelClass voClass;
    private String createVoType;
    private TopLevelClass createVoClass;
    private String updateVoType;
    private TopLevelClass updateVoClass;
    private String viewVOType;
    private TopLevelClass viewVOClass;
    private String excelVoType;
    private TopLevelClass excelVoClass;
    private String requestVoType;
    private TopLevelClass requestVoClass;
    private String cachePoType;
    private TopLevelClass cachePoClass;
    private boolean generated = false;
    private VoGenService voGenService;

    public ViewObjectClassGenerator(String project) {
        super(project);
    }

    @Override
    public List<CompilationUnit> getCompilationUnits() {
        List<CompilationUnit> answer = new ArrayList<>();
        commentGenerator = context.getCommentGenerator();
        this.voGenService = new VoGenService(introspectedTable);
        FullyQualifiedTable table = introspectedTable.getFullyQualifiedTable();
        progressCallback.startTask(getString("Progress.78", table.toString()));
        Plugin plugins = context.getPlugins();
        tc = introspectedTable.getTableConfiguration();
        FullyQualifiedJavaType entityType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        abstractName = "Abstract" + entityType.getShortName() + "VO";
        VOGeneratorConfiguration voGeneratorConfiguration = tc.getVoGeneratorConfiguration();
        baseTargetPackage = StringUtility.substringBeforeLast(context.getJavaModelGeneratorConfiguration().getTargetPackage(), ".") + ".pojo";

        /*
         * 生成AbstractVo
         * */
        String abstractVoType = String.join(".", baseTargetPackage, subPackageAbs, abstractName);
        TopLevelClass abstractVo = new TopLevelClass(abstractVoType);
        abstractVo.setAbstract(true);
        abstractVo.setVisibility(JavaVisibility.PUBLIC);
        abstractVo.addSuperInterface(new FullyQualifiedJavaType(ConstantsUtil.I_BASE_DTO));
        abstractVo.addImportedType(ConstantsUtil.I_BASE_DTO);
        commentGenerator.addJavaFileComment(abstractVo);
        commentGenerator.addModelClassComment(abstractVo, introspectedTable);
        abstractVo.addAnnotation("@Data");
        abstractVo.addAnnotation("@NoArgsConstructor");
        if (voGenService.getAbstractVOColumns().size() > 0) {
            abstractVo.addAnnotation("@AllArgsConstructor");
        }
        abstractVo.addImportedType("lombok.*");
        abstractVo.addSerialVersionUID();
        //添加属性
        for (IntrospectedColumn introspectedColumn : voGenService.getAbstractVOColumns()) {
            Field field = getJavaBeansField(introspectedColumn, context, introspectedTable);
            field.setVisibility(JavaVisibility.PROTECTED);
            if (plugins.voAbstractFieldGenerated(field, abstractVo, introspectedColumn, introspectedTable)) {
                abstractVo.addField(field);
                abstractVo.addImportedType(field.getType());

                StringBuilder sb = new StringBuilder(introspectedColumn.getJavaProperty());
                if (sb.length() > 1 && Character.isUpperCase(sb.charAt(1))) {
                    Method method = getJavaBeansGetter(introspectedColumn, context, introspectedTable);
                    abstractVo.addMethod(method);
                    if (!introspectedTable.isImmutable()) {
                        method = getJavaBeansSetter(introspectedColumn, context, introspectedTable);
                        abstractVo.addMethod(method);
                    }
                }
            }
        }
        List<String> absExampleFields = abstractVo.getFields().stream().map(Field::getName).collect(Collectors.toList());
        introspectedTable.getTopLevelClassExampleFields().put(abstractVo.getType().getShortName(), absExampleFields);
        if (context.getPlugins().voModelAbstractClassGenerated(abstractVo, introspectedTable)) {
            answer.add(abstractVo);
        }

        /*
         * 生成VO类
         * */
        if (introspectedTable.getRules().isGenerateVoModel()) {
            generated = true;
            VOModelGeneratorConfiguration voModelGeneratorConfiguration = voGeneratorConfiguration.getVoModelConfiguration();
            voType = voModelGeneratorConfiguration.getFullyQualifiedJavaType().getFullyQualifiedName();
            voClass = createTopLevelClass(voType, abstractVoType);
            voClass.addMultipleImports("lombok");
            voClass.addAnnotation("@NoArgsConstructor");
            getApiModel(voModelGeneratorConfiguration.getFullyQualifiedJavaType().getShortName()).addAnnotationToTopLevelClass(voClass);
            voClass.addSerialVersionUID();
            //添加id、version属性
            List<String> fields = new ArrayList<>(Arrays.asList("id", "version"));
            List<IntrospectedColumn> introspectedColumns = voGenService.getAllVoColumns(fields, voModelGeneratorConfiguration.getIncludeColumns(), voModelGeneratorConfiguration.getExcludeColumns());
            for (IntrospectedColumn introspectedColumn : introspectedColumns) {
                if (!isAbstractVOColumn(introspectedColumn)) {
                    Field field = getJavaBeansField(introspectedColumn, context, introspectedTable);
                    if (plugins.voModelFieldGenerated(field, voClass, introspectedColumn, introspectedTable)) {
                        voClass.addField(field);
                        voClass.addImportedType(field.getType());
                    }
                }
            }
            for (IntrospectedColumn introspectedColumn : voGenService.getAbstractVOColumns()) {
                if (!(introspectedColumn.isNullable() || introspectedTable.getTableConfiguration().getValidateIgnoreColumns().contains(introspectedColumn.getActualColumnName()))) {
                    this.getOverrideGetter(introspectedColumn).ifPresent(m -> {
                        if (plugins.voModelGetterMethodGenerated(m, voClass, introspectedColumn, introspectedTable)) {
                            voClass.addMethod(m);
                        }
                    });
                }
            }

            //增加映射
            List<OverridePropertyValueGeneratorConfiguration> overridePropertyVo = voModelGeneratorConfiguration.getOverridePropertyConfigurations();
            overridePropertyVo.addAll(voGeneratorConfiguration.getOverridePropertyConfigurations());
            voGenService.buildOverrideColumn(overridePropertyVo, voClass, ModelClassTypeEnum.voClass).forEach(field -> {
                plugins.voModelFieldGenerated(field, voClass, null, introspectedTable);
            });


            //附加属性
            List<VoAdditionalPropertyGeneratorConfiguration> additionalPropertyVo = voModelGeneratorConfiguration.getAdditionalPropertyConfigurations();
            additionalPropertyVo.addAll(voGeneratorConfiguration.getAdditionalPropertyConfigurations());
            voClass.getAddtionalPropertiesFields(additionalPropertyVo).forEach(field -> {
                if (plugins.voModelFieldGenerated(field, voClass, null, introspectedTable)) {
                    voClass.addField(field);
                    voClass.addImportedType(field.getType());
                }
            });

            voClass.addImportedType(abstractVoType);
            //persistenceBeanName属性
            Field persistenceBeanName = new Field("persistenceBeanName", FullyQualifiedJavaType.getStringInstance());
            persistenceBeanName.setVisibility(JavaVisibility.PRIVATE);
            persistenceBeanName.setRemark("对象服务java bean名称");
            ApiModelProperty apiModelProperty = new ApiModelProperty(persistenceBeanName.getRemark());
            persistenceBeanName.addAnnotation(apiModelProperty.toAnnotation());
            voClass.addMultipleImports(apiModelProperty.multipleImports());
            voClass.addField(persistenceBeanName);

            //检查是否有定制的新属性
            if (tc.getRelationGeneratorConfigurations().size() > 0) {
                /*
                 * 根据联合查询属性配置
                 * 增加相应的属性
                 */
                if (introspectedTable.getRelationGeneratorConfigurations().size() > 0) {
                    for (RelationGeneratorConfiguration relationProperty : introspectedTable.getRelationGeneratorConfigurations()) {
                        FullyQualifiedJavaType returnType;
                        Field field;
                        FullyQualifiedJavaType fullyQualifiedJavaType = new FullyQualifiedJavaType(relationProperty.getVoModelTye());
                        if (relationProperty.getType().equals(RelationTypeEnum.collection)) {
                            voClass.addImportedType(FullyQualifiedJavaType.getNewListInstance());
                            returnType = FullyQualifiedJavaType.getNewListInstance();
                            returnType.addTypeArgument(fullyQualifiedJavaType);
                        } else {
                            returnType = fullyQualifiedJavaType;
                        }
                        field = new Field(relationProperty.getPropertyName(), returnType);
                        field.setVisibility(JavaVisibility.PRIVATE);
                        field.setRemark(relationProperty.getRemark());
                        new ApiModelProperty(field.getRemark(), JDBCUtil.getExampleByClassName(field.getType().getFullyQualifiedName()))
                                .addAnnotationToField(field, voClass);
                        voClass.addField(field, null, true);
                        voClass.addImportedType(fullyQualifiedJavaType);
                    }
                }
            }
            List<String> voExampleFields = voClass.getFields().stream().map(Field::getName).collect(Collectors.toList());
            voExampleFields.addAll(absExampleFields);
            introspectedTable.getTopLevelClassExampleFields().put(voClass.getType().getShortName(), voExampleFields);

            if (introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.modelVo.name()) || fileNotExist(subPackageVo, voModelGeneratorConfiguration.getFullyQualifiedJavaType().getShortName())) {
                if (context.getPlugins().voModelRecordClassGenerated(voClass, introspectedTable)) {
                    answer.add(voClass);
                }
            }
        }

        /*
         *  生成createVo类
         *  */
        if (introspectedTable.getRules().isGenerateCreateVO()) {
            VOCreateGeneratorConfiguration voCreateGeneratorConfiguration = voGeneratorConfiguration.getVoCreateConfiguration();
            generated = true;
            baseTargetPackage = voCreateGeneratorConfiguration.getBaseTargetPackage();
            abstractVoType = String.join(".", baseTargetPackage, subPackageAbs, abstractName);
            createVoType = voCreateGeneratorConfiguration.getFullyQualifiedJavaType().getFullyQualifiedName();
            createVoClass = createTopLevelClass(createVoType, abstractVoType);
            createVoClass.addMultipleImports("lombok");
            getApiModel(voCreateGeneratorConfiguration.getFullyQualifiedJavaType().getShortName()).addAnnotationToTopLevelClass(createVoClass);
            createVoClass.addImportedType(abstractVoType);
            createVoClass.addSerialVersionUID();

            //添加id属性
            List<String> fields = new ArrayList<>(Collections.singletonList("id"));
            List<IntrospectedColumn> introspectedColumns = voGenService.getAllVoColumns(fields, voCreateGeneratorConfiguration.getIncludeColumns(), voCreateGeneratorConfiguration.getExcludeColumns());

            for (IntrospectedColumn voColumn : introspectedColumns) {
                if (!(isAbstractVOColumn(voColumn) || voCreateGeneratorConfiguration.getExcludeColumns().contains(voColumn.getActualColumnName()))) {
                    Field field = getJavaBeansField(voColumn, context, introspectedTable);
                    field.setVisibility(JavaVisibility.PRIVATE);
                    if (plugins.voCreateFieldGenerated(field, createVoClass, voColumn, introspectedTable)) {
                        createVoClass.addField(field);
                        createVoClass.addImportedType(field.getType());
                    }
                }
            }
            //增加是否选择性更新
            if (introspectedTable.getRules().createEnableSelective()) {
                Field selectiveUpdate = new Field("selectiveUpdate", FullyQualifiedJavaType.getBooleanPrimitiveInstance());
                selectiveUpdate.setVisibility(JavaVisibility.PRIVATE);
                selectiveUpdate.setRemark("插入时选择性更新");
                ApiModelProperty apiModelProperty = new ApiModelProperty(selectiveUpdate.getRemark());
                apiModelProperty.setExample("true");
                selectiveUpdate.addAnnotation(apiModelProperty.toAnnotation());
                createVoClass.addMultipleImports(apiModelProperty.multipleImports());
                createVoClass.addField(selectiveUpdate);
            }

            for (IntrospectedColumn introspectedColumn : voGenService.getAbstractVOColumns()) {
                if (!(introspectedColumn.isNullable() || introspectedTable.getTableConfiguration().getValidateIgnoreColumns().contains(introspectedColumn.getActualColumnName()))) {
                    this.getOverrideGetter(introspectedColumn).ifPresent(javaBeansGetter -> {
                        if (plugins.voCreateGetterMethodGenerated(javaBeansGetter, createVoClass, introspectedColumn, introspectedTable)) {
                            createVoClass.addMethod(javaBeansGetter);
                        }
                    });
                }
            }

            //附加属性
            List<VoAdditionalPropertyGeneratorConfiguration> additionalPropertyConfigurations = voCreateGeneratorConfiguration.getAdditionalPropertyConfigurations();
            additionalPropertyConfigurations.addAll(voGeneratorConfiguration.getAdditionalPropertyConfigurations());
            createVoClass.getAddtionalPropertiesFields(additionalPropertyConfigurations).forEach(field -> {
                        if (plugins.voCreateFieldGenerated(field, createVoClass, null, introspectedTable)) {
                            createVoClass.addField(field);
                            createVoClass.addImportedType(field.getType());
                        }
                    }
            );

            //是否有启用insert的JavaCollectionRelation
            tc.getRelationGeneratorConfigurations().stream()
                    .filter(RelationGeneratorConfiguration::isEnableInsert).collect(Collectors.toList())
                    .forEach(c -> {
                        if (!createVoClass.isContainField(c.getPropertyName())) {
                            FullyQualifiedJavaType type;
                            if (c.getType().equals(RelationTypeEnum.collection)) {
                                type = FullyQualifiedJavaType.getNewListInstance();
                                type.addTypeArgument(new FullyQualifiedJavaType(c.getVoModelTye()));
                                createVoClass.addImportedType(FullyQualifiedJavaType.getNewListInstance());
                            } else {
                                type = new FullyQualifiedJavaType(c.getVoModelTye());
                            }
                            Field field = new Field(c.getPropertyName(), type);
                            field.setVisibility(JavaVisibility.PRIVATE);
                            field.setRemark(c.getRemark());
                            createVoClass.addField(field);
                            createVoClass.addImportedType(c.getVoModelTye());
                        }
                    });
            if (introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.createVo.name()) || fileNotExist(subPackageVo, voCreateGeneratorConfiguration.getFullyQualifiedJavaType().getShortName())) {
                if (context.getPlugins().voModelCreateClassGenerated(createVoClass, introspectedTable)) {
                    answer.add(createVoClass);
                }
            }
        }
        /*
         *  生成updateVo类
         *  */
        if (introspectedTable.getRules().isGenerateUpdateVO()) {
            VOUpdateGeneratorConfiguration voUpdateGeneratorConfiguration = voGeneratorConfiguration.getVoUpdateConfiguration();
            generated = true;
            baseTargetPackage = voUpdateGeneratorConfiguration.getBaseTargetPackage();
            abstractVoType = String.join(".", baseTargetPackage, subPackageAbs, abstractName);
            updateVoType = voUpdateGeneratorConfiguration.getFullyQualifiedJavaType().getFullyQualifiedName();
            updateVoClass = createTopLevelClass(updateVoType, abstractVoType);
            updateVoClass.addMultipleImports("lombok");
            getApiModel(voUpdateGeneratorConfiguration.getFullyQualifiedJavaType().getShortName()).addAnnotationToTopLevelClass(updateVoClass);
            updateVoClass.addImportedType(abstractVoType);
            updateVoClass.addSerialVersionUID();

            //添加id属性
            List<String> fields = new ArrayList<>(Collections.singletonList("id"));
            List<IntrospectedColumn> introspectedColumns = voGenService.getAllVoColumns(fields, voUpdateGeneratorConfiguration.getIncludeColumns(), voUpdateGeneratorConfiguration.getExcludeColumns());

            for (IntrospectedColumn voColumn : introspectedColumns) {
                if (!(isAbstractVOColumn(voColumn) || voUpdateGeneratorConfiguration.getExcludeColumns().contains(voColumn.getActualColumnName()))) {
                    Field field = new Field(voColumn.getJavaProperty(), voColumn.getFullyQualifiedJavaType());
                    field.setVisibility(JavaVisibility.PRIVATE);
                    field.setRemark(voColumn.getRemarks(true));
                    if (plugins.voUpdateFieldGenerated(field, updateVoClass, voColumn, introspectedTable)) {
                        updateVoClass.addField(field);
                        updateVoClass.addImportedType(voColumn.getFullyQualifiedJavaType());
                    }
                }
            }
            for (IntrospectedColumn introspectedColumn : voGenService.getAbstractVOColumns()) {
                if (!(introspectedColumn.isNullable() || introspectedTable.getTableConfiguration().getValidateIgnoreColumns().contains(introspectedColumn.getActualColumnName()))) {
                    this.getOverrideGetter(introspectedColumn).ifPresent(javaBeansGetter -> {
                        if (plugins.voUpdateGetterMethodGenerated(javaBeansGetter, updateVoClass, introspectedColumn, introspectedTable)) {
                            updateVoClass.addMethod(javaBeansGetter);
                        }
                    });
                }
            }

            //附加属性
            List<VoAdditionalPropertyGeneratorConfiguration> additionalPropertyConfigurations = voUpdateGeneratorConfiguration.getAdditionalPropertyConfigurations();
            additionalPropertyConfigurations.addAll(voGeneratorConfiguration.getAdditionalPropertyConfigurations());
            updateVoClass.getAddtionalPropertiesFields(additionalPropertyConfigurations).forEach(field -> {
                        if (plugins.voUpdateFieldGenerated(field, updateVoClass, null, introspectedTable)) {
                            updateVoClass.addField(field);
                            updateVoClass.addImportedType(field.getType());
                        }
                    }
            );

            //是否增加是否选择性更新的属性
            if (voUpdateGeneratorConfiguration.isEnableSelective()) {
                Field selectiveUpdate = new Field("selectiveUpdate", FullyQualifiedJavaType.getBooleanPrimitiveInstance());
                selectiveUpdate.setVisibility(JavaVisibility.PRIVATE);
                selectiveUpdate.setRemark("更新时选择性插入");
                ApiModelProperty apiModelProperty = new ApiModelProperty(selectiveUpdate.getRemark());
                apiModelProperty.setExample("true");
                selectiveUpdate.addAnnotation(apiModelProperty.toAnnotation());
                updateVoClass.addMultipleImports(apiModelProperty.multipleImports());
                updateVoClass.addField(selectiveUpdate);
            }

            //是否有启用update的JavaCollectionRelation
            tc.getRelationGeneratorConfigurations().stream()
                    .filter(RelationGeneratorConfiguration::isEnableUpdate).collect(Collectors.toList())
                    .forEach(c -> {
                        if (!updateVoClass.isContainField(c.getPropertyName())) {
                            FullyQualifiedJavaType type;
                            if (c.getType().equals(RelationTypeEnum.collection)) {
                                type = FullyQualifiedJavaType.getNewListInstance();
                                type.addTypeArgument(new FullyQualifiedJavaType(c.getVoModelTye()));
                                updateVoClass.addImportedType(FullyQualifiedJavaType.getNewListInstance());
                            } else {
                                type = new FullyQualifiedJavaType(c.getVoModelTye());
                            }
                            Field field = new Field(c.getPropertyName(), type);
                            field.setVisibility(JavaVisibility.PRIVATE);
                            field.setRemark(c.getRemark());
                            updateVoClass.addField(field);
                            updateVoClass.addImportedType(c.getVoModelTye());
                        }
                    });
            if (introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.updateVo.name()) || fileNotExist(subPackageVo, voUpdateGeneratorConfiguration.getFullyQualifiedJavaType().getShortName())) {
                if (context.getPlugins().voModelUpdateClassGenerated(updateVoClass, introspectedTable)) {
                    answer.add(updateVoClass);
                }
            }
        }

        /*
         * 生成viewVo类
         * */
        if (introspectedTable.getRules().isGenerateViewVO()) {
            VOViewGeneratorConfiguration voViewGeneratorConfiguration = voGeneratorConfiguration.getVoViewConfiguration();
            generated = true;
            baseTargetPackage = voViewGeneratorConfiguration.getBaseTargetPackage();
            abstractVoType = String.join(".", baseTargetPackage, subPackageAbs, abstractName);

            viewVOType = voViewGeneratorConfiguration.getFullyQualifiedJavaType().getFullyQualifiedName();
            viewVOClass = createTopLevelClass(viewVOType, abstractVoType);
            viewVOClass.addMultipleImports("lombok");
            getApiModel(voViewGeneratorConfiguration.getFullyQualifiedJavaType().getShortName()).addAnnotationToTopLevelClass(viewVOClass);
            addViewTableMeta(voViewGeneratorConfiguration);
            viewVOClass.addImportedType(abstractVoType);
            viewVOClass.addSerialVersionUID();

            List<IntrospectedColumn> introspectedColumns = voGenService.getAllVoColumns(null, voViewGeneratorConfiguration.getIncludeColumns(), voViewGeneratorConfiguration.getExcludeColumns());
            for (IntrospectedColumn voColumn : introspectedColumns) {
                if (!(isAbstractVOColumn(voColumn) || voViewGeneratorConfiguration.getExcludeColumns().contains(voColumn.getActualColumnName()))) {
                    Field field = new Field(voColumn.getJavaProperty(), voColumn.getFullyQualifiedJavaType());
                    field.setVisibility(JavaVisibility.PRIVATE);
                    field.setRemark(voColumn.getRemarks(true));
                    if (plugins.voViewFieldGenerated(field, viewVOClass, voColumn, introspectedTable)) {
                        viewVOClass.addField(field);
                        viewVOClass.addImportedType(voColumn.getFullyQualifiedJavaType());
                    }
                }
            }

            //增加映射
            List<OverridePropertyValueGeneratorConfiguration> overridePropertyConfigurations = voViewGeneratorConfiguration.getOverridePropertyConfigurations();
            overridePropertyConfigurations.addAll(voGeneratorConfiguration.getOverridePropertyConfigurations());
            voGenService.buildOverrideColumn(overridePropertyConfigurations, viewVOClass, ModelClassTypeEnum.viewVOClass).forEach(field -> {
                        plugins.voViewFieldGenerated(field, viewVOClass, null, introspectedTable);
                    }
            );

            //附加属性
            List<VoAdditionalPropertyGeneratorConfiguration> additionalPropertyConfigurations = voViewGeneratorConfiguration.getAdditionalPropertyConfigurations();
            additionalPropertyConfigurations.addAll(voGeneratorConfiguration.getAdditionalPropertyConfigurations());
            viewVOClass.getAddtionalPropertiesFields(additionalPropertyConfigurations).forEach(field -> {
                        if (plugins.voViewFieldGenerated(field, viewVOClass, null, introspectedTable)) {
                            viewVOClass.addField(field);
                            viewVOClass.addImportedType(field.getType());
                        }
                    }
            );

            //添加生成viewVO到生成列表及菜单项
            boolean genViewVo = introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.viewVo.name()) || fileNotExist(subPackageVo, voViewGeneratorConfiguration.getFullyQualifiedJavaType().getShortName());
            if (genViewVo) {
                if (context.getPlugins().voModelViewClassGenerated(viewVOClass, introspectedTable)) {
                    answer.add(viewVOClass);
                    //添加菜单项
                    String parentMenuId = Optional.ofNullable(voViewGeneratorConfiguration.getParentMenuId())
                            .orElse(context.getParentMenuId());
                    if (stringHasValue(parentMenuId) && context.isUpdateMenuData() && introspectedTable.getTableConfiguration().isModules()) {
                        int sort = context.getSysMenuDataScriptLines().size() + 1;
                        String id = VMD5Util.MD5(introspectedTable.getControllerBeanName() + GlobalConstant.DEFAULT_VIEW_ID_SUFFIX);
                        String title = introspectedTable.getRemarks(true);
                        InsertSqlBuilder sqlBuilder = GenerateSqlTemplate.insertSqlForMenu();
                        sqlBuilder.updateStringValues("id_", id);
                        sqlBuilder.updateStringValues("name_", title);
                        sqlBuilder.updateStringValues("parent_id", parentMenuId);
                        sqlBuilder.updateStringValues("sort_", String.valueOf(sort));
                        sqlBuilder.updateStringValues("title_", title);
                        sqlBuilder.updateStringValues("url_", "viewmgr/" + id + "/openview");
                        sqlBuilder.updateStringValues("notes_", context.getModuleName() + "->" + title + "默认视图");
                        introspectedTable.getContext().addSysMenuDataScriptLines(id, sqlBuilder.toSql()+";");
                    }
                }

            }
        }

        /*
         *  生成ExcelVO
         *  */
        if (introspectedTable.getRules().isGenerateExcelVO()) {
            VOExcelGeneratorConfiguration voExcelGeneratorConfiguration = voGeneratorConfiguration.getVoExcelConfiguration();
            baseTargetPackage = voExcelGeneratorConfiguration.getBaseTargetPackage();
            excelVoType = voExcelGeneratorConfiguration.getFullyQualifiedJavaType().getFullyQualifiedName();
            excelVoClass = createTopLevelClass(excelVoType, null);
            FullyQualifiedJavaType superInterface = new FullyQualifiedJavaType(ConstantsUtil.I_BASE_DTO);
            excelVoClass.addSuperInterface(superInterface);
            excelVoClass.addImportedType(superInterface);
            excelVoClass.addImportedType("lombok.*");
            excelVoClass.addAnnotation("@Data");
            excelVoClass.addAnnotation("@Builder");
            excelVoClass.addAnnotation("@NoArgsConstructor");
            excelVoClass.addSerialVersionUID();
            //添加属性
            List<IntrospectedColumn> introspectedColumns = voGenService.getAllVoColumns(null, voExcelGeneratorConfiguration.getIncludeColumns(), voExcelGeneratorConfiguration.getExcludeColumns());
            CollectionUtil.addAllIfNotContains(introspectedColumns, voGenService.getAbstractVOColumns());
            if (introspectedColumns.size() > 0) {
                excelVoClass.addAnnotation("@AllArgsConstructor");
            }
            for (IntrospectedColumn voColumn : introspectedColumns) {
                Field field = new Field(voColumn.getJavaProperty(), voColumn.getFullyQualifiedJavaType());
                field.setVisibility(JavaVisibility.PRIVATE);
                field.setRemark(voColumn.getRemarks(true));
                if (plugins.voExcelFieldGenerated(field, excelVoClass, voColumn, introspectedTable)) {
                    excelVoClass.addField(field);
                    excelVoClass.addImportedType(voColumn.getFullyQualifiedJavaType());
                }
            }

            //增加映射
            List<OverridePropertyValueGeneratorConfiguration> overridePropertyConfigurations = voExcelGeneratorConfiguration.getOverridePropertyConfigurations();
            overridePropertyConfigurations.addAll(voGeneratorConfiguration.getOverridePropertyConfigurations());
            voGenService.buildOverrideColumn(overridePropertyConfigurations, excelVoClass, ModelClassTypeEnum.excelVoClass).forEach(field -> {
                plugins.voExcelFieldGenerated(field, excelVoClass, null, introspectedTable);
            });

            //附加属性
            List<VoAdditionalPropertyGeneratorConfiguration> additionalPropertyConfigurations = voExcelGeneratorConfiguration.getAdditionalPropertyConfigurations();
            additionalPropertyConfigurations.addAll(voGeneratorConfiguration.getAdditionalPropertyConfigurations());
            excelVoClass.getAddtionalPropertiesFields(additionalPropertyConfigurations).forEach(field -> {
                        if (plugins.voExcelFieldGenerated(field, excelVoClass, null, introspectedTable)) {
                            excelVoClass.addField(field);
                            excelVoClass.addImportedType(field.getType());
                        }
                    }
            );

            //添加生成excelVO到生成列表
            if (introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.excelVo.name()) || fileNotExist(subPackageVo, voExcelGeneratorConfiguration.getFullyQualifiedJavaType().getShortName())) {
                if (context.getPlugins().voModelExcelClassGenerated(excelVoClass, introspectedTable)) {
                    answer.add(excelVoClass);
                }
            }
        }

        /*
         * 生成RequestVO
         * */
        if (introspectedTable.getRules().isGenerateRequestVO()) {
            VORequestGeneratorConfiguration voRequestGeneratorConfiguration = voGeneratorConfiguration.getVoRequestConfiguration();
            generated = true;
            baseTargetPackage = voRequestGeneratorConfiguration.getBaseTargetPackage();
            abstractVoType = String.join(".", baseTargetPackage, subPackageAbs, abstractName);
            requestVoType = voRequestGeneratorConfiguration.getFullyQualifiedJavaType().getFullyQualifiedName();
            requestVoClass = createTopLevelClass(requestVoType, abstractVoType);
            requestVoClass.addMultipleImports("lombok");
            getApiModel(voRequestGeneratorConfiguration.getFullyQualifiedJavaType().getShortName()).addAnnotationToTopLevelClass(requestVoClass);
            requestVoClass.addSerialVersionUID();

            List<IntrospectedColumn> introspectedColumns = voGenService.getAllVoColumns(null, null, voRequestGeneratorConfiguration.getExcludeColumns());
            for (IntrospectedColumn voColumn : introspectedColumns) {
                if (!(isAbstractVOColumn(voColumn) || voRequestGeneratorConfiguration.getExcludeColumns().contains(voColumn.getActualColumnName()))) {
                    Field field = new Field(voColumn.getJavaProperty(), voColumn.getFullyQualifiedJavaType());
                    field.setVisibility(JavaVisibility.PRIVATE);
                    field.setRemark(voColumn.getRemarks(true));
                    if (plugins.voRequestFieldGenerated(field, requestVoClass, voColumn, introspectedTable)) {
                        requestVoClass.addField(field);
                        requestVoClass.addImportedType(voColumn.getFullyQualifiedJavaType());
                    }
                }
            }

            //分页属性
            if (voRequestGeneratorConfiguration.isIncludePageParam()) {
                FullyQualifiedJavaType pageType = new FullyQualifiedJavaType("com.vgosoft.core.pojo.IPage");
                requestVoClass.addSuperInterface(pageType);
                requestVoClass.addImportedType(pageType);
                Field pNo = new Field("pageNo", FullyQualifiedJavaType.getIntInstance());
                pNo.setVisibility(JavaVisibility.PRIVATE);
                pNo.setRemark("页码");
                pNo.setInitializationString("DEFAULT_FIRST_PAGE_NO");
                new ApiModelProperty(pNo.getRemark(), "1").addAnnotationToField(pNo, requestVoClass);
                requestVoClass.addField(pNo);
                Field pSize = new Field("pageSize", FullyQualifiedJavaType.getIntInstance());
                pSize.setRemark("每页数据数量");
                new ApiModelProperty(pSize.getRemark(), "10").addAnnotationToField(pSize, requestVoClass);
                pSize.setInitializationString("DEFAULT_PAGE_SIZE");
                pSize.setVisibility(JavaVisibility.PRIVATE);
                requestVoClass.addField(pSize);
            }
            Field orderByClause = new Field("orderByClause", FullyQualifiedJavaType.getStringInstance());
            orderByClause.setVisibility(JavaVisibility.PRIVATE);
            orderByClause.setRemark("排序语句");
            new ApiModelProperty(orderByClause.getRemark(), "SORT_").addAnnotationToField(orderByClause, requestVoClass);
            requestVoClass.addField(orderByClause);
            //增加cascade开关
            if (introspectedTable.getRules().generateRelationWithSubSelected()) {
                Field cascade = new Field("cascadeResult", FullyQualifiedJavaType.getBooleanPrimitiveInstance());
                cascade.setVisibility(JavaVisibility.PRIVATE);
                cascade.setRemark("结果是否包含子级");
                new ApiModelProperty(cascade.getRemark(), "false").addAnnotationToField(cascade, requestVoClass);
                requestVoClass.addField(cascade);
            }
            //增加between的other属性
            voRequestGeneratorConfiguration.getVoNameFragmentGeneratorConfigurations().stream()
                    .filter(c -> "Between".equals(c.getFragment()))
                    .forEach(c -> introspectedTable.getColumn(c.getColumn()).ifPresent(column -> {
                        Field field = JavaBeansUtil.getJavaBeansField(column, context, introspectedTable);
                        field.setVisibility(JavaVisibility.PRIVATE);
                        addFieldToTopLevelClass(field, requestVoClass, abstractVo);
                        Field other = JavaBeansUtil.getJavaBeansField(column, context, introspectedTable);
                        other.setName(other.getName() + "Other");
                        other.setVisibility(JavaVisibility.PRIVATE);
                        addFieldToTopLevelClass(other, requestVoClass, abstractVo);
                    }));

            //附加属性
            List<VoAdditionalPropertyGeneratorConfiguration> additionalPropertyConfigurations = voRequestGeneratorConfiguration.getAdditionalPropertyConfigurations();
            additionalPropertyConfigurations.addAll(voGeneratorConfiguration.getAdditionalPropertyConfigurations());
            requestVoClass.getAddtionalPropertiesFields(additionalPropertyConfigurations).forEach(field -> {
                        if (plugins.voRequestFieldGenerated(field, requestVoClass, null, introspectedTable)) {
                            requestVoClass.addField(field);
                            requestVoClass.addImportedType(field.getType());
                        }
                    }
            );
            List<String> requestExampleFields = requestVoClass.getFields().stream().map(Field::getName).collect(Collectors.toList());
            requestExampleFields.addAll(absExampleFields);
            introspectedTable.getTopLevelClassExampleFields().put(requestVoClass.getType().getShortName(), requestExampleFields);

            if (introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.requestVo.name()) || fileNotExist(subPackageVo, voRequestGeneratorConfiguration.getFullyQualifiedJavaType().getShortName())) {
                if (context.getPlugins().voModelRequestClassGenerated(requestVoClass, introspectedTable)) {
                    generated = true;
                    answer.add(requestVoClass);
                }
            }

        }

        /*
         *  生成cachePo类
         *  */
        if (introspectedTable.getRules().isGenerateCachePO()) {
            final VOCacheGeneratorConfiguration config = tc.getVoCacheGeneratorConfiguration();
            baseTargetPackage = config.getBaseTargetPackage();
            cachePoType = config.getFullyQualifiedJavaType().getFullyQualifiedName();
            cachePoClass = createTopLevelClass(cachePoType, "");
            cachePoClass.addSuperInterface(new FullyQualifiedJavaType(ConstantsUtil.I_BASE_DTO));
            cachePoClass.addImportedType(ConstantsUtil.I_BASE_DTO);
            cachePoClass.addImportedType("lombok.*");
            cachePoClass.addAnnotation("@Data");
            cachePoClass.addSerialVersionUID();

            List<IntrospectedColumn> pkColumns = introspectedTable.getPrimaryKeyColumns();
            List<IntrospectedColumn> oColumns = Stream.of(config.getCodeColumn(), config.getTypeColumn(), config.getValueColumn())
                    .distinct()
                    .map(c -> introspectedTable.getColumn(c).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<IntrospectedColumn> includeColumns = introspectedTable.getBaseColumns().stream()
                    .filter(c -> config.getIncludeColumns().contains(c.getActualColumnName()))
                    .collect(Collectors.toList());
            List<IntrospectedColumn> allColumns = Stream.of(pkColumns.stream(), oColumns.stream(), includeColumns.stream())
                    .flatMap(Function.identity())
                    .distinct()
                    .collect(Collectors.toList());
            for (IntrospectedColumn column : allColumns) {
                Field field = getJavaBeansField(column, context, introspectedTable);
                cachePoClass.addField(field);
                cachePoClass.addImportedType(field.getType());
            }

            //追加dictValueText属性
            IntrospectedColumn valueColumn = introspectedTable.getColumn(config.getValueColumn()).orElse(null);
            long dictValueCount = cachePoClass.getFields().stream()
                    .filter(f -> f.getName().equalsIgnoreCase(ConstantsUtil.PROP_DICT_VALUE_TEXT))
                    .count();
            if (dictValueCount == 0 && valueColumn != null) {
                Field field = new Field(ConstantsUtil.PROP_DICT_VALUE_TEXT, FullyQualifiedJavaType.getStringInstance());
                field.setVisibility(JavaVisibility.PRIVATE);
                field.setRemark("字典应用的返回值");
                field.addJavaDocLine("/**");
                field.addJavaDocLine("* 字典应用的返回值");
                field.addJavaDocLine("*/");
                cachePoClass.addField(field);
            }

            if (context.getPlugins().voModelCacheClassGenerated(cachePoClass, introspectedTable)) {
                generated = true;
                answer.add(cachePoClass);
            }
        }

        //生成mapstruct接口
        if (generated) {
            String mappingsName = entityType.getShortName() + "Mappings";

            String mappingsType = String.join(".", baseTargetPackage, subPackageMaps, mappingsName);
            Interface mappingsInterface = new Interface(mappingsType);
            mappingsInterface.setVisibility(JavaVisibility.PUBLIC);
            commentGenerator.addJavaFileComment(mappingsInterface);
            mappingsInterface.addImportedType(entityType);
            mappingsInterface.addImportedType(new FullyQualifiedJavaType("org.mapstruct.Mapper"));
            mappingsInterface.addImportedType(new FullyQualifiedJavaType("org.mapstruct.factory.Mappers"));
            mappingsInterface.addImportedType(FullyQualifiedJavaType.getNewListInstance());
            mappingsInterface.addAnnotation("@Mapper(componentModel = \"spring\",unmappedTargetPolicy = ReportingPolicy.IGNORE)");
            mappingsInterface.addImportedType(new FullyQualifiedJavaType("org.mapstruct.ReportingPolicy"));
            Field instance = new Field("INSTANCE", new FullyQualifiedJavaType(mappingsType));
            instance.setInitializationString(VStringUtil.format("Mappers.getMapper({0}.class)", mappingsInterface.getType().getShortName()));
            mappingsInterface.addField(instance);
            //先添加指定的转换方法
            voGeneratorConfiguration.getMappingConfigurations().forEach(c -> {
                FullyQualifiedJavaType source = new FullyQualifiedJavaType(c.getSourceType());
                c.getSourceArguments().forEach(s -> {
                    FullyQualifiedJavaType type = new FullyQualifiedJavaType(s);
                    source.addTypeArgument(type);
                    mappingsInterface.addImportedType(type);
                });
                FullyQualifiedJavaType target = new FullyQualifiedJavaType(c.getTargetType());
                c.getTargetArguments().forEach(s -> {
                    FullyQualifiedJavaType type = new FullyQualifiedJavaType(s);
                    target.addTypeArgument(type);
                    mappingsInterface.addImportedType(type);
                });
                mappingsInterface.addImportedType(source);
                mappingsInterface.addImportedType(target);
                mappingsInterface.addMethod(addMappingMethod(source, target, c.getType().equals("list")));
            });

            if (stringHasValue(voType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(voType));
                mappingsInterface.addMethod(addMappingMethod(voClass.getType(), entityType, false));
                mappingsInterface.addMethod(addMappingMethod(entityType, voClass.getType(), false));
                mappingsInterface.addMethod(addMappingMethod(voClass.getType(), entityType, true));
                mappingsInterface.addMethod(addMappingMethod(entityType, voClass.getType(), true));
            }

            if (stringHasValue(createVoType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(createVoType));
                mappingsInterface.addMethod(addMappingMethod(createVoClass.getType(), entityType, false));
                mappingsInterface.addMethod(addMappingMethod(createVoClass.getType(), entityType, true));
            }

            if (stringHasValue(updateVoType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(updateVoType));
                mappingsInterface.addMethod(addMappingMethod(updateVoClass.getType(), entityType, false));
                mappingsInterface.addMethod(addMappingMethod(updateVoClass.getType(), entityType, true));
            }

            if (stringHasValue(excelVoType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(excelVoType));
                mappingsInterface.addMethod(addMappingMethod(entityType, excelVoClass.getType(), false));
                mappingsInterface.addMethod(addMappingMethod(entityType, excelVoClass.getType(), true));
                mappingsInterface.addMethod(addMappingMethod(excelVoClass.getType(), entityType, false));
                mappingsInterface.addMethod(addMappingMethod(excelVoClass.getType(), entityType, true));
            }

            if (stringHasValue(viewVOType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(viewVOType));
                mappingsInterface.addMethod(addMappingMethod(entityType, viewVOClass.getType(), false));
                mappingsInterface.addMethod(addMappingMethod(entityType, viewVOClass.getType(), true));
            }
            if (stringHasValue(requestVoType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(requestVoType));
                mappingsInterface.addMethod(addMappingMethod(requestVoClass.getType(), entityType, false));
            }
            if (stringHasValue(cachePoType)) {
                mappingsInterface.addImportedType(new FullyQualifiedJavaType(cachePoType));
                Method method = addMappingMethod(entityType, cachePoClass.getType(), false);
                String valueColumn = tc.getVoCacheGeneratorConfiguration().getValueColumn();
                if (valueColumn != null) {
                    IntrospectedColumn column = introspectedTable.getColumn(valueColumn).orElse(null);
                    if (column != null) {
                        String a = VStringUtil.format("@Mapping(source = \"{0}\",target = \"dictValueText\")"
                                , column.getJavaProperty());
                        method.addAnnotation(a);
                        mappingsInterface.addImportedType(new FullyQualifiedJavaType("org.mapstruct.Mapping"));
                    }
                }
                mappingsInterface.addMethod(method);
                mappingsInterface.addMethod(addMappingMethod(entityType, cachePoClass.getType(), true));
            }
            if (introspectedTable.getRules().isForceGenerateScalableElement(ScalableElementEnum.maps.name()) || fileNotExist(subPackageMaps, mappingsName)) {
                answer.add(mappingsInterface);
            }
        }
        return answer;
    }

    private Optional<Method> getOverrideGetter(IntrospectedColumn introspectedColumn) {
        if (!voGenService.getAbstractVOColumns().contains(introspectedColumn)) {
            //重写getter，添加validate
            Method javaBeansGetter = JavaBeansUtil.getJavaBeansGetter(introspectedColumn, context, introspectedTable);
            if (isAbstractVOColumn(introspectedColumn)) {
                javaBeansGetter.addAnnotation("@Override");
            }
            return Optional.of(javaBeansGetter);
        }
        return Optional.empty();
    }

    private void addViewTableMeta(VOViewGeneratorConfiguration voViewGeneratorConfiguration) {
        ViewTableMeta viewTableMeta = new ViewTableMeta(introspectedTable);
        //createUrl
        String createUrl = "";
        FullyQualifiedJavaType rootType = new FullyQualifiedJavaType(getRootClass());
        if (stringHasValue(rootType.getShortName())) {
            if (EntityAbstractParentEnum.ofCode(rootType.getShortName()) == null
                    || (EntityAbstractParentEnum.ofCode(rootType.getShortName()) != null
                    && EntityAbstractParentEnum.ofCode(rootType.getShortName()).scope() != 1)) {
                createUrl = String.join("/"
                        , introspectedTable.getControllerSimplePackage()
                        , introspectedTable.getControllerBeanName()
                        , "view");
            }
        }
        if (stringHasValue(createUrl)) {
            viewTableMeta.setCreateUrl(createUrl);
        }
        //dataUrl
        viewTableMeta.setDataUrl(String.join("/"
                , introspectedTable.getControllerSimplePackage()
                , introspectedTable.getControllerBeanName()
                , "getdtdata"));

        //indexColumn
        ViewIndexColumnEnum viewIndexColumnEnum = ViewIndexColumnEnum.ofCode(voViewGeneratorConfiguration.getIndexColumn());
        if (viewIndexColumnEnum != null) {
            viewTableMeta.setIndexColumn(viewIndexColumnEnum);
        }
        //actionColumn
        if (voViewGeneratorConfiguration.getActionColumn().size() > 0) {
            ViewActionColumnEnum[] viewActionColumnEnums = voViewGeneratorConfiguration.getActionColumn().stream()
                    .map(ViewActionColumnEnum::ofCode)
                    .filter(Objects::nonNull)
                    .distinct().toArray(ViewActionColumnEnum[]::new);
            viewTableMeta.setActionColumn(viewActionColumnEnums);
        }
        //querys
        if (voViewGeneratorConfiguration.getQueryColumns().size() > 0) {
            String[] strings = voViewGeneratorConfiguration.getQueryColumns().stream()
                    .distinct()
                    .map(f -> introspectedTable.getColumn(f).orElse(null))
                    .filter(Objects::nonNull)
                    .map(c -> CompositeQuery.create(c).toAnnotation())
                    .toArray(String[]::new);
            viewTableMeta.setQuerys(strings);
        }
        //columns
        if (voViewGeneratorConfiguration.getIncludeColumns().size() > 0) {
            String[] strings = voViewGeneratorConfiguration.getIncludeColumns().stream()
                    .distinct()
                    .map(f -> introspectedTable.getColumn(f).orElse(null))
                    .filter(Objects::nonNull)
                    .map(c -> ViewColumnMeta.create(c, introspectedTable).toAnnotation())
                    .toArray(String[]::new);
            viewTableMeta.setColumns(strings);
        }
        //ignoreFields
        if (voViewGeneratorConfiguration.getExcludeColumns().size() > 0) {
            String[] columns2 = voViewGeneratorConfiguration.getExcludeColumns().stream()
                    .distinct()
                    .map(f -> introspectedTable.getColumn(f).orElse(null))
                    .filter(Objects::nonNull)
                    .map(IntrospectedColumn::getJavaProperty)
                    .toArray(String[]::new);
            viewTableMeta.setIgnoreFields(columns2);
        }
        //className
        viewTableMeta.setClassName(viewVOType);

        //构造ViewTableMeta
        viewVOClass.addAnnotation(viewTableMeta.toAnnotation());
        viewVOClass.addMultipleImports(viewTableMeta.multipleImports());
    }

    private ApiModel getApiModel(String voModelName) {
        ApiModel apiModel = ApiModel.create(voModelName);
        apiModel.setParent(abstractName + ".class");
        apiModel.setDescription(introspectedTable.getRemarks(true));
        return apiModel;
    }

    private boolean fileNotExist(String subPackage, String fileName) {
        File project = new File(getProject());
        if (!project.isDirectory()) {
            return true;
        }
        File directory = new File(project, packageToDir(String.join(".", baseTargetPackage, subPackage)));
        if (!directory.isDirectory()) {
            return true;
        }
        File file = new File(directory, fileName + ".java");
        return !file.exists();
    }

    private TopLevelClass createTopLevelClass(String type, String superType) {
        TopLevelClass topLevelClass = new TopLevelClass(type);
        topLevelClass.setVisibility(JavaVisibility.PUBLIC);
        commentGenerator.addJavaFileComment(topLevelClass);
        if (!VStringUtil.isBlank(superType)) {
            topLevelClass.addImportedType(superType);
            topLevelClass.setSuperClass(superType);
        }
        return topLevelClass;
    }

    private Method addMappingMethod(FullyQualifiedJavaType fromType, FullyQualifiedJavaType toType, boolean isList) {
        String methodName;
        Method method;
        FullyQualifiedJavaType entityType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        if (entityType.getFullyQualifiedName().equalsIgnoreCase(toType.getFullyQualifiedName())) {
            methodName = "from" + fromType.getShortNameWithoutTypeArguments();
        } else if (entityType.getFullyQualifiedName().equalsIgnoreCase(fromType.getFullyQualifiedName())) {
            methodName = "to" + toType.getShortNameWithoutTypeArguments();
        } else {
            methodName = "from" + fromType.getShortNameWithoutTypeArguments() + "To" + toType.getShortNameWithoutTypeArguments();
        }
        if (isList) {
            methodName = methodName + "s";
            method = new Method(methodName);
            FullyQualifiedJavaType listInstanceFrom = FullyQualifiedJavaType.getNewListInstance();
            listInstanceFrom.addTypeArgument(fromType);
            method.addParameter(new Parameter(listInstanceFrom, JavaBeansUtil.getFirstCharacterLowercase(fromType.getShortNameWithoutTypeArguments()) + "s"));
            FullyQualifiedJavaType listInstanceTo = FullyQualifiedJavaType.getNewListInstance();
            listInstanceTo.addTypeArgument(toType);
            method.setReturnType(listInstanceTo);
        } else {
            method = new Method(methodName);
            method.addParameter(new Parameter(fromType, JavaBeansUtil.getFirstCharacterLowercase(fromType.getShortNameWithoutTypeArguments())));
            method.setReturnType(toType);
        }
        method.setAbstract(true);
        return method;
    }

    private boolean isAbstractVOColumn(IntrospectedColumn introspectedColumn) {
        return voGenService.getAbstractVOColumns()
                .stream()
                .map(IntrospectedColumn::getActualColumnName)
                .anyMatch(t -> t.equalsIgnoreCase(introspectedColumn.getActualColumnName()));
    }

    private void addFieldToTopLevelClass(final Field field, TopLevelClass topLevelClass, TopLevelClass abstractVo) {
        if (Stream.of(topLevelClass.getFields().stream(), abstractVo.getFields().stream())
                .flatMap(Function.identity())
                .noneMatch(f -> field.getName().equals(f.getName()))) {
            topLevelClass.addField(field);
            topLevelClass.addImportedType(field.getType());
        }
    }
}
