package fr.inria.spirals.repairnator.process.step;

import ch.qos.logback.classic.Level;
import fr.inria.jtravis.entities.Build;
import fr.inria.spirals.repairnator.BuildToBeInspected;
import fr.inria.spirals.repairnator.process.step.paths.ComputeSourceDir;
import fr.inria.spirals.repairnator.process.testinformation.ErrorType;
import fr.inria.spirals.repairnator.utils.Utils;
import fr.inria.spirals.repairnator.config.RepairnatorConfig;
import fr.inria.spirals.repairnator.process.files.FileHelper;
import fr.inria.spirals.repairnator.process.inspectors.JobStatus;
import fr.inria.spirals.repairnator.process.inspectors.ProjectInspector;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutBuggyBuild;
import fr.inria.spirals.repairnator.process.step.checkoutrepository.CheckoutType;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldFail;
import fr.inria.spirals.repairnator.process.step.gatherinfo.BuildShouldPass;
import fr.inria.spirals.repairnator.process.step.gatherinfo.GatherTestInformation;
import fr.inria.spirals.repairnator.process.testinformation.FailureLocation;
import fr.inria.spirals.repairnator.process.testinformation.FailureType;
import fr.inria.spirals.repairnator.process.utils4tests.ProjectInspectorMocker;
import fr.inria.spirals.repairnator.serializer.AbstractDataSerializer;
import fr.inria.spirals.repairnator.states.PipelineState;
import fr.inria.spirals.repairnator.states.ScannedBuildStatus;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by urli on 07/03/2017.
 */
public class TestGatherTestInformation {

    private File tmpDir;

    @Before
    public void setup() {
        Utils.setLoggersLevel(Level.ERROR);
        RepairnatorConfig config = RepairnatorConfig.getInstance();
        config.setJTravisEndpoint("https://api.travis-ci.com");
    }

    @After
    public void tearDown() throws IOException {
        RepairnatorConfig.deleteInstance();
        FileHelper.deleteFile(tmpDir);
    }

    @Test
    @Ignore
    //FIXME: We can't rely on repairnator/failing project to get builds
    public void testGatherTestInformationWhenFailing() throws IOException {
        long buildId = 220951452; // repairnator/failingProject

        Build build = this.checkBuildAndReturn(buildId, false);

        tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        System.out.println("Dirpath : "+tmpDir.toPath());

        File repoDir = new File(tmpDir, "repo");
        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);

        cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
                .addNextStep(new TestProject(inspector))
                .addNextStep(new ComputeSourceDir(inspector, true, false))
                .addNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(5));
        StepStatus gatherTestInfoStatus = stepStatusList.get(4);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is("test failure"));

        assertThat(jobStatus.getFailingModulePath(), is(repoDir.getCanonicalPath()));
        assertThat(gatherTestInformation.getNbTotalTests(), is(98));
        assertThat(gatherTestInformation.getNbFailingTests(), is(26));
        assertThat(gatherTestInformation.getNbErroringTests(), is(5));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        List<String> failureNames = jobStatus.getFailureNames();

        assertThat(failureNames.contains("java.lang.StringIndexOutOfBoundsException"), is(true));
        assertThat(failureNames.size(), is(5));

        assertThat(jobStatus.getFailureLocations().size(), is(10));
    }

    @Test
    @Ignore
    //FIXME: We can't rely on repairnator/failing project to get builds
    public void testGatherTestInformationOnlyOneErroring() throws IOException {
        long buildId = 220944190; // repairnator/failingProject only-one-failing

        Build build = this.checkBuildAndReturn(buildId, false);

        tmpDir = Files.createTempDirectory("test_gathertest").toFile();

        File repoDir = new File(tmpDir, "repo");
        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
                .addNextStep(new TestProject(inspector))
                .addNextStep(new ComputeSourceDir(inspector, true, false))
                .addNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(5));
        StepStatus gatherTestInfoStatus = stepStatusList.get(4);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is("test failure"));

        assertThat(jobStatus.getFailingModulePath(), is(repoDir.getCanonicalPath()));
        assertThat(gatherTestInformation.getNbTotalTests(), is(8));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(1));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        List<String> failureNames = jobStatus.getFailureNames();

        assertThat("failure names"+ StringUtils.join(failureNames.toArray()), failureNames.contains("java.lang.StringIndexOutOfBoundsException"), is(true));
        assertThat(failureNames.size(), is(1));

        assertThat(jobStatus.getFailureLocations().size(), is(1));

        FailureLocation expectedFailureLocation = new FailureLocation("nopol_examples.nopol_example_1.NopolExampleTest");
        ErrorType errorType = new ErrorType("java.lang.StringIndexOutOfBoundsException", "java.lang.StringIndexOutOfBoundsException: String index out of range: -5\n" +
                "\tat java.base/java.lang.StringLatin1.charAt(StringLatin1.java:47)\n" +
                "\tat java.base/java.lang.String.charAt(String.java:693)\n" +
                "\tat nopol_examples.nopol_example_1.NopolExample.charAt(NopolExample.java:16)\n" +
                "\tat nopol_examples.nopol_example_1.NopolExampleTest.test5(NopolExampleTest.java:39)\n" +
                "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n" +
                "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n" +
                "\tat java.base/java.lang.reflect.Method.invoke(Method.java:566)\n" +
                "\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:47)\n" +
                "\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n" +
                "\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:44)\n" +
                "\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n" +
                "\tat org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:271)\n" +
                "\tat org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:70)\n" +
                "\tat org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:50)\n" +
                "\tat org.junit.runners.ParentRunner$3.run(ParentRunner.java:238)\n" +
                "\tat org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:63)\n" +
                "\tat org.junit.runners.ParentRunner.runChildren(ParentRunner.java:236)\n" +
                "\tat org.junit.runners.ParentRunner.access$000(ParentRunner.java:53)\n" +
                "\tat org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:229)\n" +
                "\tat org.junit.runners.ParentRunner.run(ParentRunner.java:309)\n" +
                "\tat org.apache.maven.surefire.junit4.JUnit4Provider.execute(JUnit4Provider.java:252)\n" +
                "\tat org.apache.maven.surefire.junit4.JUnit4Provider.executeTestSet(JUnit4Provider.java:141)\n" +
                "\tat org.apache.maven.surefire.junit4.JUnit4Provider.invoke(JUnit4Provider.java:112)\n" +
                "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
                "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n" +
                "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n" +
                "\tat java.base/java.lang.reflect.Method.invoke(Method.java:566)\n" +
                "\tat org.apache.maven.surefire.util.ReflectionUtils.invokeMethodWithArray(ReflectionUtils.java:189)\n" +
                "\tat org.apache.maven.surefire.booter.ProviderFactory$ProviderProxy.invoke(ProviderFactory.java:165)\n" +
                "\tat org.apache.maven.surefire.booter.ProviderFactory.invokeProvider(ProviderFactory.java:85)\n" +
                "\tat org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:115)\n" +
                "\tat org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:75)");
        expectedFailureLocation.addErroringMethod("test5", errorType);

        FailureLocation actualLocation = jobStatus.getFailureLocations().iterator().next();

        assertThat(actualLocation, is(expectedFailureLocation));
        assertThat(actualLocation.getErroringMethodsAndErrors().size(), is(1));
        assertTrue(actualLocation.getErroringMethodsAndErrors().containsKey("test5"));

        ErrorType actualErrorType = actualLocation.getErroringMethodsAndErrors().get("test5").get(0);

        assertThat(actualErrorType.getClassFiles().size(), is(0));
        assertThat(actualErrorType.getStackFiles().size(), is(1));
        System.out.println(actualErrorType.getStackFiles());
        assertThat(actualErrorType.getPackageFiles().size(), is(1));
        System.out.println(actualErrorType.getPackageFiles());
    }

    @Test
    @Ignore
    //FIXME: We can't rely on repairnator/failing project to get builds
    public void testGatherTestInformationWhenErroring() throws IOException {
        long buildId = 220946365; // repairnator/failingProject erroring-branch

        Build build = this.checkBuildAndReturn(buildId, false);

        tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        System.out.println("Dirpath : "+tmpDir.toPath());

        File repoDir = new File(tmpDir, "repo");

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
                .addNextStep(new TestProject(inspector))
                .addNextStep(new ComputeSourceDir(inspector, true, false))
                .addNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(5));
        StepStatus gatherTestInfoStatus = stepStatusList.get(4);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is("test failure"));

        assertThat(jobStatus.getFailingModulePath(), is(repoDir.getCanonicalPath()));
        assertThat(gatherTestInformation.getNbTotalTests(), is(26));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(5));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        List<String> failureNames = jobStatus.getFailureNames();

        assertThat("Got the following errors: "+StringUtils.join(failureNames, ","), failureNames.contains("java.lang.NullPointerException"), is(true));
        assertThat(failureNames.size(), is(3));

        assertThat(jobStatus.getFailureLocations().size(), is(4));
    }

    @Test
    @Ignore
    //FIXME: We can't rely on repairnator/failing project to get builds
    public void testGatherTestInformationWhenNotFailing() throws IOException {
        long buildId = 225938152; // https://travis-ci.com/github/repairnator/TestingProject/builds/225938152

        Build build = this.checkBuildAndReturn(buildId, false);

        tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        System.out.println("Dirpath : "+tmpDir.toPath());


        File repoDir = new File(tmpDir, "repo");

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldFail(), false);


        cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
                .addNextStep(new TestProject(inspector))
                .addNextStep(new ComputeSourceDir(inspector, true, false))
                .addNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(5));
        StepStatus gatherTestInfoStatus = stepStatusList.get(4);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            if (stepStatus.getStep() != gatherTestInformation) {
                assertThat(stepStatus.isSuccess(), is(true));
            } else {
                assertThat(stepStatus.isSuccess(), is(false));
            }

        }

        String serializedStatus = AbstractDataSerializer.getPrettyPrintState(inspector);
        assertThat(serializedStatus, is(PipelineState.NOTFAILING.name()));

        assertThat(jobStatus.getFailingModulePath(), is(tmpDir.getAbsolutePath()+"/repo"));
        assertThat(gatherTestInformation.getNbTotalTests(), is(1));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(0));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        assertThat(jobStatus.getFailureNames().size(), is(0));
        assertThat(jobStatus.getFailureLocations().size(), is(0));
    }

    @Test
    @Ignore
    //FIXME: We can't rely on repairnator/failing project to get builds
    public void testGatherTestInformationWhenNotFailingWithPassingContract() throws IOException {
        long buildId = 225938152; // https://travis-ci.com/github/repairnator/TestingProject/builds/225938152

        Build build = this.checkBuildAndReturn(buildId, false);

        tmpDir = Files.createTempDirectory("test_gathertest").toFile();
        System.out.println("Dirpath : "+tmpDir.toPath());

        BuildToBeInspected toBeInspected = new BuildToBeInspected(build, null, ScannedBuildStatus.ONLY_FAIL, "");

        JobStatus jobStatus = new JobStatus(tmpDir.getAbsolutePath()+"/repo");

        ProjectInspector inspector = ProjectInspectorMocker.mockProjectInspector(jobStatus, tmpDir, toBeInspected, CheckoutType.CHECKOUT_BUGGY_BUILD);

        CloneRepository cloneStep = new CloneRepository(inspector);
        GatherTestInformation gatherTestInformation = new GatherTestInformation(inspector, true, new BuildShouldPass(), false);


        cloneStep.addNextStep(new CheckoutBuggyBuild(inspector, true))
                .addNextStep(new TestProject(inspector))
                .addNextStep(new ComputeSourceDir(inspector, true, false))
                .addNextStep(gatherTestInformation);
        cloneStep.execute();

        List<StepStatus> stepStatusList = jobStatus.getStepStatuses();
        assertThat(stepStatusList.size(), is(5));
        StepStatus gatherTestInfoStatus = stepStatusList.get(4);
        assertThat(gatherTestInfoStatus.getStep(), is(gatherTestInformation));

        for (StepStatus stepStatus : stepStatusList) {
            assertThat(stepStatus.isSuccess(), is(true));
        }

        assertThat(jobStatus.getFailingModulePath(), is(tmpDir.getAbsolutePath()+"/repo"));
        assertThat(gatherTestInformation.getNbTotalTests(), is(1));
        assertThat(gatherTestInformation.getNbFailingTests(), is(0));
        assertThat(gatherTestInformation.getNbErroringTests(), is(0));
        assertThat(gatherTestInformation.getNbSkippingTests(), is(0));

        assertThat(jobStatus.getFailureNames().size(), is(0));
        assertThat(jobStatus.getFailureLocations().size(), is(0));
    }

    private Build checkBuildAndReturn(long buildId, boolean isPR) {
        Optional<Build> optionalBuild = RepairnatorConfig.getInstance().getJTravis().build().fromId(buildId);
        assertTrue(optionalBuild.isPresent());

        Build build = optionalBuild.get();
        assertThat(build, IsNull.notNullValue());
        assertThat(buildId, Is.is(build.getId()));
        assertThat(build.isPullRequest(), Is.is(isPR));

        return build;
    }
}
