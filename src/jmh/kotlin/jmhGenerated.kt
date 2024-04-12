package jmh.kotlin

import org.antlr.Java8Lexer
import org.antlr.Java8Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.ucfs.parser.Gll
import org.ucfs.lexer.JavaGrammar
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import org.ucfs.input.LinearInputLabel
import org.ucfs.input.RecoveryLinearInput
import org.ucfs.lexer.JavaLexer
import org.ucfs.lexer.JavaToken
import org.ucfs.rsm.symbol.Terminal
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

val pathToInput = "/src/jmh/resources/junit4SourcesProcessedErrorFree/"

fun getTokenStream(input: String): RecoveryLinearInput<Int, LinearInputLabel> {
    val inputGraph = RecoveryLinearInput<Int, LinearInputLabel>()
    val lexer = JavaLexer(StringReader(input))
    var vertexId = 0
    var token: JavaToken

    inputGraph.addStartVertex(vertexId)
    inputGraph.addVertex(vertexId)

    while (true) {
        token = lexer.yylex() as JavaToken
        if (token == JavaToken.EOF) break
        println(token.name)
        inputGraph.addEdge(vertexId, LinearInputLabel(Terminal(token)), ++vertexId)
        inputGraph.addVertex(vertexId)
    }

    return inputGraph
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
open class AntlrBenchmark {

	@Param("OneTestCase_processed.java","NotPublicTestCase_processed.java","NoTestsRemainException_processed.java","OldTests_processed.java","TestRule_processed.java","JUnit4ClassRunner_processed.java","CategoryFilterFactoryTest_processed.java","TestClassTest_processed.java","ObjectContractTest_processed.java","LoggingStatement_processed.java","ThrowableMessageMatcher_processed.java","SortingRequest_processed.java","InheritedTestTest_processed.java","SerializableValueDescription_processed.java","JUnit3Builder_processed.java","FrameworkField_processed.java","JavadocTest_processed.java","SynchronizedRunListenerTest_processed.java","Ordering_processed.java","ClassRulesTest_processed.java","ComparisonCriteria_processed.java","TestDescriptionMethodNameTest_processed.java","JUnit4TestAdapterCache_processed.java","RuleContainer_processed.java","InvalidTestClassError_processed.java","JUnit4TestCaseFacade_processed.java","StubbedTheoriesTest_processed.java","FailedConstructionTest_processed.java","ReflectiveThreadMXBean_processed.java","Theory_processed.java","DataPoint_processed.java","Ignore_processed.java","ExpectedExceptionMatcherBuilder_processed.java","BlockJUnit4ClassRunnerWithParameters_processed.java","TestSystem_processed.java","TestWithParametersTest_processed.java","MultiCategoryTest_processed.java","AllMembersSupplier_processed.java","AnnotationsValidator_processed.java","ActiveTestSuite_processed.java","AssertTest_processed.java","RunListener_processed.java","Assume_processed.java","DataPoints_processed.java","TheoryTestUtils_processed.java","AllDefaultPossibilitiesBuilder_processed.java","TestRuleTest_processed.java","AllAssertionTests_processed.java","InvalidOrderingException_processed.java","ResultPrinter_processed.java","AllManipulationTests_processed.java","TextListenerTest_processed.java","Sortable_processed.java","ParameterizedNamesTest_processed.java","ParameterSignature_processed.java","RunnerBuilderStub_processed.java","ValidationTest_processed.java","StubbedTheories_processed.java","SuiteMethodBuilder_processed.java","AllRunnersTests_processed.java","PotentialAssignment_processed.java","StacktracePrintingMatcher_processed.java","Filterable_processed.java","SystemExitTest_processed.java","Filter_processed.java","MainRunner_processed.java","Result_processed.java","TemporaryFolderUsageTest_processed.java","AllTestsTest_processed.java","MultipleFailureException_processed.java","AssertionFailedError_processed.java","ParallelComputer_processed.java","AfterClass_processed.java","UseSuiteAsASuperclassTest_processed.java","ClassLevelMethodsWithIgnoredTestsTest_processed.java","MethodRulesTest_processed.java","Correspondent_processed.java","TypeMatchingBetweenMultiDataPointsMethod_processed.java","ActiveTestTest_processed.java","TestWatchman_processed.java","BadlyFormedClassesTest_processed.java","TestSuite_processed.java","MaxHistory_processed.java","AllParallelTests_processed.java","ComparisonCompactor_processed.java","ParameterSupplier_processed.java","AllClassesTests_processed.java","BlockJUnit4ClassRunnerWithParametersFactory_processed.java","AnnotatedBuilderTest_processed.java","AllExperimentalTests_processed.java","OverrideTestCase_processed.java","TempFolderRuleTest_processed.java","ComparisonFailureTest_processed.java","Parameterized_processed.java","ExpectExceptionTest_processed.java","PrintableResult_processed.java","ReflectiveRuntimeMXBean_processed.java","AllCoreTests_processed.java","ComparisonFailure_processed.java","RunAfters_processed.java","AlphanumericOrdering_processed.java","TestImplementorTest_processed.java","WithParameterSupplier_processed.java","WasRun_processed.java","MultipleFailureExceptionTest_processed.java","RuleChainTest_processed.java","TestListener_processed.java","Statement_processed.java","RepeatedTestTest_processed.java","BlockJUnit4ClassRunner_processed.java","FilterOptionIntegrationTest_processed.java","TestCaseTest_processed.java","ExpectedTest_processed.java","TextRunnerTest_processed.java","EnclosedTest_processed.java","InexactComparisonCriteria_processed.java","OrderWith_processed.java","IMoney_processed.java","UnsuccessfulWithDataPointFields_processed.java","Theories_processed.java","OrderableTest_processed.java","Protectable_processed.java","StacktracePrintingMatcherTest_processed.java","Description_processed.java","BlockJUnit4ClassRunnerTest_processed.java","ParentRunnerTest_processed.java","SuiteTest_processed.java","WithAutoGeneratedDataPoints_processed.java","ExpectException_processed.java","BaseTestRunnerTest_processed.java","TestDescriptionTest_processed.java","SynchronizedRunListener_processed.java","AllParameterizedTests_processed.java","AllModelTests_processed.java","Comparators_processed.java","ThreeTestCases_processed.java","RuleChain_processed.java","Computer_processed.java","TestClass_processed.java","SuiteDescriptionTest_processed.java","MaxCore_processed.java","CustomBlockJUnit4ClassRunnerTest_processed.java","MemoizingRequest_processed.java","ErrorReportingRunnerTest_processed.java","JUnit38ClassRunner_processed.java","TextListener_processed.java","FakeRuntimeMXBean_processed.java","PublicClassValidatorTest_processed.java","Timeout_processed.java","StopwatchTest_processed.java","ConcurrentRunNotifierTest_processed.java","TestCouldNotBeSkippedException_processed.java","Success_processed.java","LoggingMethodRule_processed.java","FilterFactoryParams_processed.java","AssumptionTest_processed.java","WithExtendedParameterSources_processed.java","FilterableTest_processed.java","AllDescriptionTests_processed.java","JUnit4_processed.java","AllRunnerTests_processed.java","SuiteMethodTest_processed.java","SingleMethodTest_processed.java","Describable_processed.java","JUnit4Builder_processed.java","FrameworkFieldTest_processed.java","IgnoreClassTest_processed.java","JUnitCommandLineParseResultTest_processed.java","MatcherTest_processed.java","ThrowableCauseMatcherTest_processed.java","AssumptionViolatedException_processed.java","CategoryValidatorTest_processed.java","ParentRunnerFilteringTest_processed.java","Orderable_processed.java","TestMethodTest_processed.java","ExternalResource_processed.java","ValidateWith_processed.java","AllCategoriesTests_processed.java","ExcludeCategories_processed.java","StoppedByUserException_processed.java","ParameterizedTestMethodTest_processed.java","FilterFactoriesTest_processed.java","RunBefores_processed.java","AllResultsTests_processed.java","ComparisonCompactorTest_processed.java","PrintableResultTest_processed.java","SampleJUnit3Tests_processed.java","ResultTest_processed.java","WithOnlyTestAnnotations_processed.java","Super_processed.java","TestedOn_processed.java","NullBuilder_processed.java","NoTestCases_processed.java","TestMethod_processed.java","Annotatable_processed.java","AssumptionViolatedExceptionTest_processed.java","NoArgTestCaseTest_processed.java","ErrorCollector_processed.java","AllSamplesTests_processed.java","RealSystem_processed.java","MethodSorterTest_processed.java","ForwardCompatibilityPrintingTest_processed.java","Guesser_processed.java","RepeatedTest_processed.java","TestSetup_processed.java","FilterFactory_processed.java","RuleContainerTest_processed.java","CouldNotReadCoreException_processed.java","ErrorReportingRunner_processed.java","FakeThreadMXBean_processed.java","VerifierRuleTest_processed.java","OrderWithValidator_processed.java","SpecificDataPointsSupplierTest_processed.java","WithDataPointMethod_processed.java","MethodValidator_processed.java","MemberValueConsumer_processed.java","ParentRunnerClassLoaderTest_processed.java","AllMethodsTests_processed.java","AllMembersSupplierTest_processed.java","RunRules_processed.java","ListenerTest_processed.java","RequestTest_processed.java","IgnoredBuilder_processed.java","IgnoredClassRunner_processed.java","SuiteMethod_processed.java","JUnitCoreTest_processed.java","TestResult_processed.java","NoGenericTypeParametersValidator_processed.java","RuleMemberValidator_processed.java","InitializationError_processed.java","Failure_processed.java","FilterTest_processed.java","ForwardCompatibilityTest_processed.java","RunnerBuilder_processed.java","AllTheoriesInternalTests_processed.java","IncludeCategories_processed.java","SuccessfulWithDataPointFields_processed.java","MaxStarterTest_processed.java","PublicClassValidator_processed.java","TypeSafeMatcher_processed.java","RuleMemberValidatorTest_processed.java","Stopwatch_processed.java","JUnitCommandLineParseResult_processed.java","RunWithTest_processed.java","TestWithParameters_processed.java","Categories_processed.java","MoneyBag_processed.java","JUnitMatchers_processed.java","FilterRequest_processed.java","ReguessableValue_processed.java","Stub_processed.java","OrderingRequest_processed.java","TimeoutRuleTest_processed.java","EnumSupplier_processed.java","JUnit38SortingTest_processed.java","AllInternalTests_processed.java","FailureList_processed.java","EventCollector_processed.java","MethodRule_processed.java","RunnerScheduler_processed.java","ErrorCollectorTest_processed.java","ArrayComparisonFailureTest_processed.java","InheritedTestCase_processed.java","ManagementFactory_processed.java","FailOnTimeout_processed.java","MethodSorters_processed.java","Category_processed.java","Checks_processed.java","AllJUnit3CompatibilityTests_processed.java","EachTestNotifier_processed.java","CategoryValidator_processed.java","ReflectiveCallable_processed.java","WithUnresolvedGenericTypeVariablesOnTheoryParms_processed.java","ParallelMethodTest_processed.java","TestWatcher_processed.java","TheoriesPerformanceTest_processed.java","TestListenerTest_processed.java","TestWithClassRule_processed.java","Fail_processed.java","ThrowableCauseMatcher_processed.java","DisableOnDebug_processed.java","AnnotationValidatorFactory_processed.java","RunnerSpy_processed.java","ParallelClassTest_processed.java","ParameterSignatureTest_processed.java","FrameworkMember_processed.java","TimeoutTest_processed.java","MethodRoadie_processed.java","OrderWithValidatorTest_processed.java","TemporaryFolder_processed.java","JUnit4TestAdapterTest_processed.java","NameRulesTest_processed.java","ListTest_processed.java","FailOnTimeoutTest_processed.java","ParameterizedAssertionError_processed.java","TestedOnSupplierTest_processed.java","TestName_processed.java","ParametersSuppliedBy_processed.java","Sub_processed.java","AllMaxTests_processed.java","Sorter_processed.java","ExternalResourceRuleTest_processed.java","ThreadsTest_processed.java","Version_processed.java","BaseTestRunner_processed.java","LoggingTestWatcher_processed.java","ExactComparisonCriteria_processed.java","Suite_processed.java","Before_processed.java","FailedBefore_processed.java","MoneyTest_processed.java","ExpectedException_processed.java","ExtensionTest_processed.java","RunNotifierTest_processed.java","OldTestClassAdaptingListenerTest_processed.java","Test_processed.java","AllNotificationTests_processed.java","ResultMatchersTest_processed.java","SampleJUnit4Tests_processed.java","ParentRunner_processed.java","AnnotationTest_processed.java","ThreadMXBean_processed.java","TestCase_processed.java","AnnotationValidator_processed.java","Money_processed.java","Assert_processed.java","DescriptionTest_processed.java","JUnit4TestAdapter_processed.java","WhenNoParametersMatch_processed.java","TestedOnSupplier_processed.java","ParameterizedTestTest_processed.java","FromDataPoints_processed.java","InvokeMethod_processed.java","ParameterizedAssertionErrorTest_processed.java","ClassRequest_processed.java","FilterFactories_processed.java","AllTests_processed.java","StackFilterTest_processed.java","RunWith_processed.java","UserStopTest_processed.java","TestWatchmanTest_processed.java","AllValidationTests_processed.java","Rule_processed.java","PotentialAssignmentTest_processed.java","Runner_processed.java","MethodSorter_processed.java","SortableTest_processed.java","AllTheoriesRunnerTests_processed.java","ValidationError_processed.java","ReverseAlphanumericSorter_processed.java","FailingDataPointMethods_processed.java","Enclosed_processed.java","FixMethodOrder_processed.java","AnnotationValidatorFactoryTest_processed.java","AnnotationsValidatorTest_processed.java","NoTestCaseClass_processed.java","TestWatcherTest_processed.java","NotVoidTestCase_processed.java","TestClassValidator_processed.java","SerializableMatcherDescription_processed.java","JUnit38ClassRunnerTest_processed.java","StringableObject_processed.java","AssertionFailedErrorTest_processed.java","LoggingTestRule_processed.java","package-info_processed.java","AnnotatedDescriptionTest_processed.java","DisableOnDebugTest_processed.java","RunnerTest_processed.java","GuesserQueue_processed.java","ArrayComparisonFailure_processed.java","Classes_processed.java","CategoryTest_processed.java","After_processed.java","OrderWithTest_processed.java","AnnotatedBuilder_processed.java","AllRunningTests_processed.java","WithNamedDataPoints_processed.java","JUnitSystem_processed.java","CommandLineTest_processed.java","CategoryFilterFactory_processed.java","ComparatorBasedOrdering_processed.java","Assignments_processed.java","BeforeClass_processed.java","CategoriesAndParameterizedTest_processed.java","TestTimedOutException_processed.java","TestDecorator_processed.java","TestRunListener_processed.java","InitializationErrorForwardCompatibilityTest_processed.java","AllDeprecatedTests_processed.java","BlockJUnit4ClassRunnerOverrideTest_processed.java","FrameworkMethod_processed.java","JUnitCore_processed.java","AllListeningTests_processed.java","TextFeedbackTest_processed.java","AllValidatorTests_processed.java","JUnitCoreReturnsCorrectExitCodeTest_processed.java","BlockJUnit4ClassRunnerWithParametersTest_processed.java","RunNotifier_processed.java","ThrowingRunnable_processed.java","RuntimeMXBean_processed.java","SpecificDataPointsSupplier_processed.java","TextRunnerSingleMethodTest_processed.java","AllRulesTests_processed.java","Alphanumeric_processed.java","StackTracesTest_processed.java","ClassRule_processed.java","AssumingInTheoriesTest_processed.java","TestFailure_processed.java","InvalidTestClassErrorTest_processed.java","BooleanSupplier_processed.java","ParametersRunnerFactory_processed.java","JUnit4ClassRunnerTest_processed.java","ResultMatchers_processed.java","ClassRequestTest_processed.java","Orderer_processed.java","TemporaryFolderRuleAssuredDeletionTest_processed.java","AllTheoriesTests_processed.java","MethodCall_processed.java","FrameworkMethodTest_processed.java","Request_processed.java","ExpectedExceptionTest_processed.java","TestRunner_processed.java","Verifier_processed.java","ReverseAlphanumericOrdering_processed.java","ClassRoadie_processed.java",)
    var filename: String = ""
    
    lateinit var fileContents: String
    
    @Setup(Level.Trial)
    fun prepare() {
        fileContents = File(pathToInput + filename).readText()
    }
    
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    fun measureAntlr(blackhole: Blackhole) {
        val antlrParser = Java8Parser(CommonTokenStream(Java8Lexer(CharStreams.fromString(fileContents))))
        blackhole.consume(antlrParser.compilationUnit())
    }
}

@State(Scope.Benchmark)
open class GllBenchmark {
	@Param("OneTestCase_processed.java","NotPublicTestCase_processed.java","NoTestsRemainException_processed.java","OldTests_processed.java","TestRule_processed.java","JUnit4ClassRunner_processed.java","CategoryFilterFactoryTest_processed.java","TestClassTest_processed.java","ObjectContractTest_processed.java","LoggingStatement_processed.java","ThrowableMessageMatcher_processed.java","SortingRequest_processed.java","InheritedTestTest_processed.java","SerializableValueDescription_processed.java","JUnit3Builder_processed.java","FrameworkField_processed.java","JavadocTest_processed.java","SynchronizedRunListenerTest_processed.java","Ordering_processed.java","ClassRulesTest_processed.java","ComparisonCriteria_processed.java","TestDescriptionMethodNameTest_processed.java","JUnit4TestAdapterCache_processed.java","RuleContainer_processed.java","InvalidTestClassError_processed.java","JUnit4TestCaseFacade_processed.java","StubbedTheoriesTest_processed.java","FailedConstructionTest_processed.java","ReflectiveThreadMXBean_processed.java","Theory_processed.java","DataPoint_processed.java","Ignore_processed.java","ExpectedExceptionMatcherBuilder_processed.java","BlockJUnit4ClassRunnerWithParameters_processed.java","TestSystem_processed.java","TestWithParametersTest_processed.java","MultiCategoryTest_processed.java","AllMembersSupplier_processed.java","AnnotationsValidator_processed.java","ActiveTestSuite_processed.java","AssertTest_processed.java","RunListener_processed.java","Assume_processed.java","DataPoints_processed.java","TheoryTestUtils_processed.java","AllDefaultPossibilitiesBuilder_processed.java","TestRuleTest_processed.java","AllAssertionTests_processed.java","InvalidOrderingException_processed.java","ResultPrinter_processed.java","AllManipulationTests_processed.java","TextListenerTest_processed.java","Sortable_processed.java","ParameterizedNamesTest_processed.java","ParameterSignature_processed.java","RunnerBuilderStub_processed.java","ValidationTest_processed.java","StubbedTheories_processed.java","SuiteMethodBuilder_processed.java","AllRunnersTests_processed.java","PotentialAssignment_processed.java","StacktracePrintingMatcher_processed.java","Filterable_processed.java","SystemExitTest_processed.java","Filter_processed.java","MainRunner_processed.java","Result_processed.java","TemporaryFolderUsageTest_processed.java","AllTestsTest_processed.java","MultipleFailureException_processed.java","AssertionFailedError_processed.java","ParallelComputer_processed.java","AfterClass_processed.java","UseSuiteAsASuperclassTest_processed.java","ClassLevelMethodsWithIgnoredTestsTest_processed.java","MethodRulesTest_processed.java","Correspondent_processed.java","TypeMatchingBetweenMultiDataPointsMethod_processed.java","ActiveTestTest_processed.java","TestWatchman_processed.java","BadlyFormedClassesTest_processed.java","TestSuite_processed.java","MaxHistory_processed.java","AllParallelTests_processed.java","ComparisonCompactor_processed.java","ParameterSupplier_processed.java","AllClassesTests_processed.java","BlockJUnit4ClassRunnerWithParametersFactory_processed.java","AnnotatedBuilderTest_processed.java","AllExperimentalTests_processed.java","OverrideTestCase_processed.java","TempFolderRuleTest_processed.java","ComparisonFailureTest_processed.java","Parameterized_processed.java","ExpectExceptionTest_processed.java","PrintableResult_processed.java","ReflectiveRuntimeMXBean_processed.java","AllCoreTests_processed.java","ComparisonFailure_processed.java","RunAfters_processed.java","AlphanumericOrdering_processed.java","TestImplementorTest_processed.java","WithParameterSupplier_processed.java","WasRun_processed.java","MultipleFailureExceptionTest_processed.java","RuleChainTest_processed.java","TestListener_processed.java","Statement_processed.java","RepeatedTestTest_processed.java","BlockJUnit4ClassRunner_processed.java","FilterOptionIntegrationTest_processed.java","TestCaseTest_processed.java","ExpectedTest_processed.java","TextRunnerTest_processed.java","EnclosedTest_processed.java","InexactComparisonCriteria_processed.java","OrderWith_processed.java","IMoney_processed.java","UnsuccessfulWithDataPointFields_processed.java","Theories_processed.java","OrderableTest_processed.java","Protectable_processed.java","StacktracePrintingMatcherTest_processed.java","Description_processed.java","BlockJUnit4ClassRunnerTest_processed.java","ParentRunnerTest_processed.java","SuiteTest_processed.java","WithAutoGeneratedDataPoints_processed.java","ExpectException_processed.java","BaseTestRunnerTest_processed.java","TestDescriptionTest_processed.java","SynchronizedRunListener_processed.java","AllParameterizedTests_processed.java","AllModelTests_processed.java","Comparators_processed.java","ThreeTestCases_processed.java","RuleChain_processed.java","Computer_processed.java","TestClass_processed.java","SuiteDescriptionTest_processed.java","MaxCore_processed.java","CustomBlockJUnit4ClassRunnerTest_processed.java","MemoizingRequest_processed.java","ErrorReportingRunnerTest_processed.java","JUnit38ClassRunner_processed.java","TextListener_processed.java","FakeRuntimeMXBean_processed.java","PublicClassValidatorTest_processed.java","Timeout_processed.java","StopwatchTest_processed.java","ConcurrentRunNotifierTest_processed.java","TestCouldNotBeSkippedException_processed.java","Success_processed.java","LoggingMethodRule_processed.java","FilterFactoryParams_processed.java","AssumptionTest_processed.java","WithExtendedParameterSources_processed.java","FilterableTest_processed.java","AllDescriptionTests_processed.java","JUnit4_processed.java","AllRunnerTests_processed.java","SuiteMethodTest_processed.java","SingleMethodTest_processed.java","Describable_processed.java","JUnit4Builder_processed.java","FrameworkFieldTest_processed.java","IgnoreClassTest_processed.java","JUnitCommandLineParseResultTest_processed.java","MatcherTest_processed.java","ThrowableCauseMatcherTest_processed.java","AssumptionViolatedException_processed.java","CategoryValidatorTest_processed.java","ParentRunnerFilteringTest_processed.java","Orderable_processed.java","TestMethodTest_processed.java","ExternalResource_processed.java","ValidateWith_processed.java","AllCategoriesTests_processed.java","ExcludeCategories_processed.java","StoppedByUserException_processed.java","ParameterizedTestMethodTest_processed.java","FilterFactoriesTest_processed.java","RunBefores_processed.java","AllResultsTests_processed.java","ComparisonCompactorTest_processed.java","PrintableResultTest_processed.java","SampleJUnit3Tests_processed.java","ResultTest_processed.java","WithOnlyTestAnnotations_processed.java","Super_processed.java","TestedOn_processed.java","NullBuilder_processed.java","NoTestCases_processed.java","TestMethod_processed.java","Annotatable_processed.java","AssumptionViolatedExceptionTest_processed.java","NoArgTestCaseTest_processed.java","ErrorCollector_processed.java","AllSamplesTests_processed.java","RealSystem_processed.java","MethodSorterTest_processed.java","ForwardCompatibilityPrintingTest_processed.java","Guesser_processed.java","RepeatedTest_processed.java","TestSetup_processed.java","FilterFactory_processed.java","RuleContainerTest_processed.java","CouldNotReadCoreException_processed.java","ErrorReportingRunner_processed.java","FakeThreadMXBean_processed.java","VerifierRuleTest_processed.java","OrderWithValidator_processed.java","SpecificDataPointsSupplierTest_processed.java","WithDataPointMethod_processed.java","MethodValidator_processed.java","MemberValueConsumer_processed.java","ParentRunnerClassLoaderTest_processed.java","AllMethodsTests_processed.java","AllMembersSupplierTest_processed.java","RunRules_processed.java","ListenerTest_processed.java","RequestTest_processed.java","IgnoredBuilder_processed.java","IgnoredClassRunner_processed.java","SuiteMethod_processed.java","JUnitCoreTest_processed.java","TestResult_processed.java","NoGenericTypeParametersValidator_processed.java","RuleMemberValidator_processed.java","InitializationError_processed.java","Failure_processed.java","FilterTest_processed.java","ForwardCompatibilityTest_processed.java","RunnerBuilder_processed.java","AllTheoriesInternalTests_processed.java","IncludeCategories_processed.java","SuccessfulWithDataPointFields_processed.java","MaxStarterTest_processed.java","PublicClassValidator_processed.java","TypeSafeMatcher_processed.java","RuleMemberValidatorTest_processed.java","Stopwatch_processed.java","JUnitCommandLineParseResult_processed.java","RunWithTest_processed.java","TestWithParameters_processed.java","Categories_processed.java","MoneyBag_processed.java","JUnitMatchers_processed.java","FilterRequest_processed.java","ReguessableValue_processed.java","Stub_processed.java","OrderingRequest_processed.java","TimeoutRuleTest_processed.java","EnumSupplier_processed.java","JUnit38SortingTest_processed.java","AllInternalTests_processed.java","FailureList_processed.java","EventCollector_processed.java","MethodRule_processed.java","RunnerScheduler_processed.java","ErrorCollectorTest_processed.java","ArrayComparisonFailureTest_processed.java","InheritedTestCase_processed.java","ManagementFactory_processed.java","FailOnTimeout_processed.java","MethodSorters_processed.java","Category_processed.java","Checks_processed.java","AllJUnit3CompatibilityTests_processed.java","EachTestNotifier_processed.java","CategoryValidator_processed.java","ReflectiveCallable_processed.java","WithUnresolvedGenericTypeVariablesOnTheoryParms_processed.java","ParallelMethodTest_processed.java","TestWatcher_processed.java","TheoriesPerformanceTest_processed.java","TestListenerTest_processed.java","TestWithClassRule_processed.java","Fail_processed.java","ThrowableCauseMatcher_processed.java","DisableOnDebug_processed.java","AnnotationValidatorFactory_processed.java","RunnerSpy_processed.java","ParallelClassTest_processed.java","ParameterSignatureTest_processed.java","FrameworkMember_processed.java","TimeoutTest_processed.java","MethodRoadie_processed.java","OrderWithValidatorTest_processed.java","TemporaryFolder_processed.java","JUnit4TestAdapterTest_processed.java","NameRulesTest_processed.java","ListTest_processed.java","FailOnTimeoutTest_processed.java","ParameterizedAssertionError_processed.java","TestedOnSupplierTest_processed.java","TestName_processed.java","ParametersSuppliedBy_processed.java","Sub_processed.java","AllMaxTests_processed.java","Sorter_processed.java","ExternalResourceRuleTest_processed.java","ThreadsTest_processed.java","Version_processed.java","BaseTestRunner_processed.java","LoggingTestWatcher_processed.java","ExactComparisonCriteria_processed.java","Suite_processed.java","Before_processed.java","FailedBefore_processed.java","MoneyTest_processed.java","ExpectedException_processed.java","ExtensionTest_processed.java","RunNotifierTest_processed.java","OldTestClassAdaptingListenerTest_processed.java","Test_processed.java","AllNotificationTests_processed.java","ResultMatchersTest_processed.java","SampleJUnit4Tests_processed.java","ParentRunner_processed.java","AnnotationTest_processed.java","ThreadMXBean_processed.java","TestCase_processed.java","AnnotationValidator_processed.java","Money_processed.java","Assert_processed.java","DescriptionTest_processed.java","JUnit4TestAdapter_processed.java","WhenNoParametersMatch_processed.java","TestedOnSupplier_processed.java","ParameterizedTestTest_processed.java","FromDataPoints_processed.java","InvokeMethod_processed.java","ParameterizedAssertionErrorTest_processed.java","ClassRequest_processed.java","FilterFactories_processed.java","AllTests_processed.java","StackFilterTest_processed.java","RunWith_processed.java","UserStopTest_processed.java","TestWatchmanTest_processed.java","AllValidationTests_processed.java","Rule_processed.java","PotentialAssignmentTest_processed.java","Runner_processed.java","MethodSorter_processed.java","SortableTest_processed.java","AllTheoriesRunnerTests_processed.java","ValidationError_processed.java","ReverseAlphanumericSorter_processed.java","FailingDataPointMethods_processed.java","Enclosed_processed.java","FixMethodOrder_processed.java","AnnotationValidatorFactoryTest_processed.java","AnnotationsValidatorTest_processed.java","NoTestCaseClass_processed.java","TestWatcherTest_processed.java","NotVoidTestCase_processed.java","TestClassValidator_processed.java","SerializableMatcherDescription_processed.java","JUnit38ClassRunnerTest_processed.java","StringableObject_processed.java","AssertionFailedErrorTest_processed.java","LoggingTestRule_processed.java","package-info_processed.java","AnnotatedDescriptionTest_processed.java","DisableOnDebugTest_processed.java","RunnerTest_processed.java","GuesserQueue_processed.java","ArrayComparisonFailure_processed.java","Classes_processed.java","CategoryTest_processed.java","After_processed.java","OrderWithTest_processed.java","AnnotatedBuilder_processed.java","AllRunningTests_processed.java","WithNamedDataPoints_processed.java","JUnitSystem_processed.java","CommandLineTest_processed.java","CategoryFilterFactory_processed.java","ComparatorBasedOrdering_processed.java","Assignments_processed.java","BeforeClass_processed.java","CategoriesAndParameterizedTest_processed.java","TestTimedOutException_processed.java","TestDecorator_processed.java","TestRunListener_processed.java","InitializationErrorForwardCompatibilityTest_processed.java","AllDeprecatedTests_processed.java","BlockJUnit4ClassRunnerOverrideTest_processed.java","FrameworkMethod_processed.java","JUnitCore_processed.java","AllListeningTests_processed.java","TextFeedbackTest_processed.java","AllValidatorTests_processed.java","JUnitCoreReturnsCorrectExitCodeTest_processed.java","BlockJUnit4ClassRunnerWithParametersTest_processed.java","RunNotifier_processed.java","ThrowingRunnable_processed.java","RuntimeMXBean_processed.java","SpecificDataPointsSupplier_processed.java","TextRunnerSingleMethodTest_processed.java","AllRulesTests_processed.java","Alphanumeric_processed.java","StackTracesTest_processed.java","ClassRule_processed.java","AssumingInTheoriesTest_processed.java","TestFailure_processed.java","InvalidTestClassErrorTest_processed.java","BooleanSupplier_processed.java","ParametersRunnerFactory_processed.java","JUnit4ClassRunnerTest_processed.java","ResultMatchers_processed.java","ClassRequestTest_processed.java","Orderer_processed.java","TemporaryFolderRuleAssuredDeletionTest_processed.java","AllTheoriesTests_processed.java","MethodCall_processed.java","FrameworkMethodTest_processed.java","Request_processed.java","ExpectedExceptionTest_processed.java","TestRunner_processed.java","Verifier_processed.java","ReverseAlphanumericOrdering_processed.java","ClassRoadie_processed.java",)
    var filename: String = ""
    
    val startState = JavaGrammar().rsm
    
    lateinit var fileContents: String
    
    @Setup(Level.Trial)
    fun prepare() {
        fileContents = File(pathToInput + filename).readText()
    }
    
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    fun measureGll(blackhole: Blackhole) {
        val inputGraph = getTokenStream(fileContents)
        val gll = Gll.recoveryGll(startState, inputGraph)
        
        blackhole.consume(gll.parse())
    }
}
