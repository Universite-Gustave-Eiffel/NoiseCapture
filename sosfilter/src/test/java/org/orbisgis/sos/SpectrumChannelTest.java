package org.orbisgis.sos;

import junit.framework.TestCase;

public class SpectrumChannelTest extends TestCase {

    public void testGenerateJsonClass() {

            JCodeModel jcodeModel = new JCodeModel();

            GenerationConfig config = new DefaultGenerationConfig() {
                @Override
                public boolean isGenerateBuilders() {
                    return true;
                }

                @Override
                public SourceType getSourceType() {
                    return SourceType.JSON;
                }
            };

            SchemaMapper mapper = new SchemaMapper(new RuleFactory(config, new Jackson2Annotator(config), new SchemaStore()), new SchemaGenerator());
            mapper.generate(jcodeModel, javaClassName, packageName, inputJsonUrl);

            jcodeModel.build(outputJavaClassDirectory);
    }

}