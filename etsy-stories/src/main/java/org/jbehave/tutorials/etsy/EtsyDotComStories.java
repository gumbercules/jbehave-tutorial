package org.jbehave.tutorials.etsy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.jbehave.core.annotations.AfterStories;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.embedder.Embedder;
import org.jbehave.core.failures.BatchFailures;
import org.jbehave.core.failures.FailingUponPendingStep;
import org.jbehave.core.io.CodeLocations;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.StoryFinder;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.reporters.CrossReference;
import org.jbehave.core.reporters.Format;
import org.jbehave.core.reporters.StoryReporterBuilder;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.InstanceStepsFactory;
import org.jbehave.core.steps.StepMonitor;
import org.jbehave.core.steps.pico.PicoStepsFactory;
import org.jbehave.web.queue.WebQueue;
import org.jbehave.web.queue.WebQueueConfiguration;
import org.jbehave.web.selenium.ContextView;
import org.jbehave.web.selenium.LocalFrameContextView;
import org.jbehave.web.selenium.PerStoryWebDriverSteps;
import org.jbehave.web.selenium.SauceContextOutput;
import org.jbehave.web.selenium.SauceWebDriverProvider;
import org.jbehave.web.selenium.SeleniumConfiguration;
import org.jbehave.web.selenium.SeleniumContext;
import org.jbehave.web.selenium.SeleniumContextOutput;
import org.jbehave.web.selenium.SeleniumStepMonitor;
import org.jbehave.web.selenium.TypeWebDriverProvider;
import org.jbehave.web.selenium.WebDriverProvider;
import org.jbehave.web.selenium.WebDriverScreenshotOnFailure;
import org.picocontainer.Characteristics;
import org.picocontainer.behaviors.ThreadCaching;
import org.picocontainer.classname.ClassLoadingPicoContainer;
import org.picocontainer.classname.ClassName;
import org.picocontainer.classname.DefaultClassLoadingPicoContainer;
import org.picocontainer.injectors.CompositeInjection;
import org.picocontainer.injectors.ConstructorInjection;
import org.picocontainer.injectors.SetterInjection;

import static java.util.Arrays.asList;
import static org.jbehave.core.io.CodeLocations.codeLocationFromClass;
import static org.jbehave.core.reporters.Format.CONSOLE;
import static org.jbehave.web.selenium.WebDriverHtmlOutput.WEB_DRIVER_HTML;

public class EtsyDotComStories extends JUnitStories {

    private WebDriverProvider driverProvider;
    private Configuration configuration;
    private ContextView contextView;
    private SeleniumContext seleniumContext = new SeleniumContext();
    private Format[] outputFormats;
    private String metaFilter;
    private final CrossReference crossReference = new CrossReference() {
        public String getMetaFilter() {
            return metaFilter;
        }
    }.withJsonOnly()
            .withOutputAfterEachStory(true);

    public EtsyDotComStories() {

        if (System.getProperty("SAUCE_USERNAME") != null) {
            driverProvider = new SauceWebDriverProvider();
            outputFormats = new Format[] { WEB_DRIVER_HTML };
            crossReference.withThreadSafeDelegateFormat(new SauceContextOutput(driverProvider));
            contextView = new ContextView.NULL();
        } else {
            outputFormats = new Format[] { new SeleniumContextOutput(seleniumContext), CONSOLE, WEB_DRIVER_HTML };
            driverProvider = new TypeWebDriverProvider();
            contextView = new LocalFrameContextView().sized(640, 120);
        }

        crossReference.excludingStoriesWithNoExecutedScenarios(true);

        StoryReporterBuilder storyReporterBuilder = new StoryReporterBuilder()
                .withCodeLocation(CodeLocations.codeLocationFromClass(EtsyDotComStories.class))
                .withFailureTrace(true)
                .withFailureTraceCompression(true)
                .withDefaultFormats()
                .withFormats(outputFormats)
                .withCrossReference(crossReference);

        addCrossReference(storyReporterBuilder, crossReference);

        configuration = new SeleniumConfiguration().useWebDriverProvider(driverProvider)
                .useSeleniumContext(seleniumContext)
                .useFailureStrategy(new FailingUponPendingStep())
                .useStepMonitor(createStepMonitor())
                .useStoryLoader(new LoadFromClasspath(EtsyDotComStories.class.getClassLoader()))
                .useStoryReporterBuilder(storyReporterBuilder);

        useConfiguration(configuration);

        List<CandidateSteps> steps = new ArrayList<CandidateSteps>();
        // Before And After Steps
        steps.addAll(new InstanceStepsFactory(configuration, new PerStoryWebDriverSteps(driverProvider),
                new PerStoriesContextView(contextView), new WebDriverScreenshotOnFailure(driverProvider, configuration
                        .storyReporterBuilder())).createCandidateSteps());
        // Groovy Steps
        final DefaultClassLoadingPicoContainer container = new DefaultClassLoadingPicoContainer(
                new ThreadCaching().wrap(new CompositeInjection(new ConstructorInjection(), new SetterInjection()
                        .withInjectionOptional())));
        container.change(Characteristics.USE_NAMES);
        container.addComponent(WebDriverProvider.class, driverProvider);
        // This loads all the Groovy classes in the 'pages' package
        container.visit(new ClassName("pages.Home"), ".*\\.class", true,
                new DefaultClassLoadingPicoContainer.ClassVisitor() {
                    public void classFound(@SuppressWarnings("rawtypes") Class clazz) {
                        container.addComponent(clazz);
                    }
                });

        ClassLoadingPicoContainer steps1 = container.makeChildContainer("steps");
        steps1.addComponent(new ClassName("housekeeping.EmptyCartIfNotAlready"));
        steps1.addComponent(new ClassName("EtsyDotComSteps"));

        steps.addAll(new PicoStepsFactory(configuration, steps1).createCandidateSteps());
        addSteps(steps);

    }

    protected StepMonitor createStepMonitor() {
        return new SeleniumStepMonitor(contextView, new SeleniumContext(), crossReference.getStepMonitor());
    }

    protected void addCrossReference(StoryReporterBuilder storyReporterBuilder, CrossReference crossReference) {
        storyReporterBuilder.withCrossReference(crossReference);
    }

    @Override
    public void run() {

        // only available post instantiation because of the way the jbehave maven plugin
        // decorates an instance with configuration
        metaFilter = super.configuredEmbedder().metaFilters().toString();

        Embedder embedder = configuredEmbedder();
        if (System.getProperty("WEB_QUEUE") != null) {
            List<Future<Embedder.ThrowableStory>> futures = new ArrayList<Future<Embedder.ThrowableStory>>();
            BatchFailures batchFailures = new BatchFailures();
            String path = codeLocationFromClass(EtsyDotComStories.class).getPath();
            WebQueue queue = null;
            try {
                File navigatorDir = new File(new File(path).getParentFile().getParentFile(), "target/jbehave/view");
                WebQueueConfiguration webConfiguration = new WebQueueConfiguration();
                webConfiguration.useNavigatorDirectory(navigatorDir);
                String port = System.getProperty("jetty.port");
                if (port == null) {
                    webConfiguration.usePort(8090);
                } else {
                    webConfiguration.usePort(Integer.parseInt(port));
                }
                queue = new WebQueue(embedder, batchFailures, futures, webConfiguration);
                queue.start();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
            }
        } else {
            embedder.runStoriesAsPaths(storyPaths());
        }

    }

    @Override
    protected List<String> storyPaths() {
        if (System.getProperty("WEB_QUEUE") != null) {
            return new ArrayList<String>();
        }
        return new StoryFinder().findPaths(codeLocationFromClass(this.getClass()).getFile(), asList("**/"
                + storyFilter() + ".story"), null);
    }

    private String storyFilter() {
        String storyFilter = System.getProperty("storyFilter");
        if (storyFilter != null) {
            return storyFilter;
        } else {
            return "*";
        }
    }

    public static class PerStoriesContextView {

        private final ContextView contextView;

        public PerStoriesContextView(ContextView contextView) {
            this.contextView = contextView;
        }

        @AfterStories
        public void afterStory() {
            contextView.close();
        }
    }

}
