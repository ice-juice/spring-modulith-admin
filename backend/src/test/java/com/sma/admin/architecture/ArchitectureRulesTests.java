package com.sma.admin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sma.admin.SpringbootModulithAdminApplication;
import com.sma.admin.common.ApiResponse;
import com.sma.admin.common.security.RequiresPermission;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * 项目级架构约束（README「框架约束」一节逐条对应）。Spring Modulith 自身的模块校验在 {@code ModularityTests}。
 * <p>
 * 规则对空集合放行（allowEmptyShould），这样在业务模块尚未出现时也能通过；一旦出现对应代码即开始约束。
 */
class ArchitectureRulesTests {

	static final String BASE = "com.sma.admin";

	static final Pattern MODULE_ID = Pattern.compile("[a-z]+");

	static final Pattern PERMISSION_KEY = Pattern.compile("[a-z]+(:[a-z][a-z0-9-]*){1,3}");

	static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(BASE);

	static final DescribedPredicate<JavaClass> MODULE_API_PACKAGE = DescribedPredicate.describe(
			"reside in a module API (root) package",
			type -> type.getPackageName().matches(Pattern.quote(BASE) + "\\.[a-z]+"));

	static final DescribedPredicate<JavaMethod> REQUEST_HANDLER = DescribedPredicate.describe(
			"are request handlers",
			method -> method.isAnnotatedWith(RequestMapping.class) || method.isMetaAnnotatedWith(RequestMapping.class));

	// R1 ---------------------------------------------------------------------------------------------------------

	@Test
	void modulesDeclareIdDisplayNameAndExplicitDependencies() {
		List<String> problems = new ArrayList<>();
		ApplicationModules.of(SpringbootModulithAdminApplication.class).forEach(module -> {
			String id = module.getIdentifier().toString();
			String pkg = module.getBasePackage().getName();
			if (!MODULE_ID.matcher(id).matches()) {
				problems.add(id + ": 模块 id 必须是单个小写英文单词（同时用作表前缀、URL 前缀、权限 key 前缀）");
			}
			ApplicationModule metadata = packageInfoAnnotation(pkg);
			if (metadata == null) {
				problems.add(id + ": package-info.java 必须声明 @ApplicationModule");
				return;
			}
			if (metadata.displayName().isBlank()) {
				problems.add(id + ": @ApplicationModule 必须声明 displayName");
			}
			if (Arrays.asList(metadata.allowedDependencies()).contains(ApplicationModule.OPEN_TOKEN)) {
				problems.add(id + ": @ApplicationModule 必须显式声明 allowedDependencies（依赖白名单）");
			}
		});
		assertThat(problems).isEmpty();
	}

	// R2 ---------------------------------------------------------------------------------------------------------

	@Test
	void commonDoesNotDependOnBusinessModules() {
		noClasses().that().resideInAPackage(BASE + ".common..")
				.should().dependOnClassesThat(DescribedPredicate.describe("belong to another application module",
						type -> type.getPackageName().startsWith(BASE + ".") && !moduleOf(type).equals("common")))
				.check(CLASSES);
	}

	// R3 ---------------------------------------------------------------------------------------------------------

	@Test
	void moduleApiDoesNotExposeInternalTypes() {
		noClasses().that(MODULE_API_PACKAGE)
				.should().dependOnClassesThat().resideInAPackage("..internal..")
				.check(CLASSES);
	}

	// R4 ---------------------------------------------------------------------------------------------------------

	@Test
	void layersInsideModulesPointDownwards() {
		layeredArchitecture().consideringOnlyDependenciesInLayers()
				.withOptionalLayers(true)
				.layer("Web").definedBy("..internal.web..")
				.layer("Service").definedBy("..internal.service..")
				.layer("Persistence").definedBy("..internal.persistence..")
				.whereLayer("Web").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service").mayOnlyBeAccessedByLayers("Web")
				.whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R5 ---------------------------------------------------------------------------------------------------------

	@Test
	void controllersLiveInWebLayerUnderModulePath() {
		classes().that().areAnnotatedWith(RestController.class)
				.should().resideInAPackage("..internal.web..")
				.andShould().haveSimpleNameEndingWith("Controller")
				.andShould(classCondition("map requests under /api/{module}", type -> {
					String prefix = type.getName().equals(BASE + ".openapi.internal.web.OpenController") ? "/open" : "/api/" + moduleOf(type);
					List<String> paths = type.tryGetAnnotationOfType(RequestMapping.class)
							.map(mapping -> Stream.concat(Arrays.stream(mapping.value()), Arrays.stream(mapping.path())).toList())
							.orElse(List.of());
					return !paths.isEmpty() && paths.stream().allMatch(p -> p.equals(prefix) || p.startsWith(prefix + "/"));
				}))
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R6 ---------------------------------------------------------------------------------------------------------

	@Test
	void everyRequestHandlerDeclaresAnAccessPolicy() {
		methods().that(REQUEST_HANDLER).and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
				.should(methodCondition("declare @RequiresPermission, @PermitLoggedIn or @PermitAnonymous (on method or class)",
						method -> method.isMetaAnnotatedWith(PreAuthorize.class) || method.isAnnotatedWith(PreAuthorize.class)
								|| method.getOwner().isMetaAnnotatedWith(PreAuthorize.class)
								|| method.getOwner().isAnnotatedWith(PreAuthorize.class)))
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R7 ---------------------------------------------------------------------------------------------------------

	@Test
	void permissionKeysAreScopedToTheirModule() {
		BiPredicate<String, String> valid = (module, key) -> PERMISSION_KEY.matcher(key).matches() && key.startsWith(module + ":");
		methods().that().areAnnotatedWith(RequiresPermission.class)
				.should(methodCondition("use a permission key '<module>:<resource>[:<action>]' of the owning module",
						method -> valid.test(moduleOf(method.getOwner()), method.getAnnotationOfType(RequiresPermission.class).value())))
				.allowEmptyShould(true)
				.check(CLASSES);
		classes().that().areAnnotatedWith(RequiresPermission.class)
				.should(classCondition("use a permission key '<module>:<resource>[:<action>]' of the owning module",
						type -> valid.test(moduleOf(type), type.getAnnotationOfType(RequiresPermission.class).value())))
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R8 ---------------------------------------------------------------------------------------------------------

	@Test
	void requestHandlersReturnUnifiedResponses() {
		methods().that(REQUEST_HANDLER).and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
				.should().haveRawReturnType(ApiResponse.class)
				.orShould().haveRawReturnType(ResponseEntity.class)
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R9 ---------------------------------------------------------------------------------------------------------

	@Test
	void persistenceTypesStayInsideTheirModule() {
		classes().that().areAssignableTo(BaseMapper.class).and().areInterfaces()
				.should().resideInAPackage("..internal.persistence..")
				.andShould().haveSimpleNameEndingWith("Mapper")
				.allowEmptyShould(true)
				.check(CLASSES);
		classes().that().areAnnotatedWith(TableName.class)
				.should().resideInAPackage("..internal.persistence..")
				.andShould(classCondition("map a table named '<module>_*'",
						type -> type.getAnnotationOfType(TableName.class).value().startsWith(moduleOf(type) + "_")))
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R10 --------------------------------------------------------------------------------------------------------
	@Test
	void tenantBypassIsRestrictedToCredentialBootstrap() {
		methods().that().areAnnotatedWith(com.baomidou.mybatisplus.annotation.InterceptorIgnore.class)
			.should(methodCondition("only bypass tenant filtering for unique credential bootstrap lookups", method ->
				(method.getOwner().getName().equals(BASE + ".openapi.internal.persistence.ApplicationBootstrapMapper") && method.getName().equals("byAppId"))
				|| (method.getOwner().getName().equals(BASE + ".openapi.internal.persistence.TokenBootstrapMapper") && method.getName().equals("byHash"))))
			.allowEmptyShould(true).check(CLASSES);
	}

	@Test
	void transactionsAreNotOpenedInTheWebLayer() {
		noClasses().that().resideInAPackage("..internal.web..")
				.should().beAnnotatedWith(Transactional.class)
				.allowEmptyShould(true)
				.check(CLASSES);
		noMethods().that().areDeclaredInClassesThat().resideInAPackage("..internal.web..")
				.should().beAnnotatedWith(Transactional.class)
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R11 --------------------------------------------------------------------------------------------------------

	@Test
	void eventsArePublicRecords() {
		classes().that().haveSimpleNameEndingWith("Event")
				.should().beRecords()
				.andShould(classCondition("reside in the module API (root) package", MODULE_API_PACKAGE))
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R12 --------------------------------------------------------------------------------------------------------

	@Test
	void crossModuleListenersUseApplicationModuleListener() {
		noMethods().should().beAnnotatedWith(TransactionalEventListener.class)
				.because("跨模块事件统一使用 @ApplicationModuleListener（异步 + 新事务 + 事件发布登记）")
				.allowEmptyShould(true)
				.check(CLASSES);
	}

	// R13 --------------------------------------------------------------------------------------------------------

	@Test
	void generalCodingRules() {
		GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
		GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
		GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
		GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME.check(CLASSES);
		GeneralCodingRules.OLD_DATE_AND_TIME_CLASSES_SHOULD_NOT_BE_USED.check(CLASSES);
	}

	// helpers ----------------------------------------------------------------------------------------------------

	static String moduleOf(JavaClass type) {
		String pkg = type.getPackageName();
		return pkg.startsWith(BASE + ".") ? pkg.substring(BASE.length() + 1).split("\\.")[0] : "";
	}

	static ApplicationModule packageInfoAnnotation(String packageName) {
		try {
			return Class.forName(packageName + ".package-info").getAnnotation(ApplicationModule.class);
		}
		catch (ClassNotFoundException ex) {
			return null;
		}
	}

	static ArchCondition<JavaClass> classCondition(String description, Predicate<? super JavaClass> test) {
		return condition(description, test, JavaClass::getName);
	}

	static ArchCondition<JavaMethod> methodCondition(String description, Predicate<? super JavaMethod> test) {
		return condition(description, test, JavaMethod::getFullName);
	}

	static <T> ArchCondition<T> condition(String description, Predicate<? super T> test, Function<T, String> name) {
		return new ArchCondition<>(description) {
			@Override
			public void check(T item, ConditionEvents events) {
				boolean satisfied = test.test(item);
				events.add(new SimpleConditionEvent(item, satisfied,
						name.apply(item) + (satisfied ? " does " : " does not ") + description));
			}
		};
	}

}
