<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
 xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
 xmlns="http://www.opengis.net/sld" 
 xmlns:ogc="http://www.opengis.net/ogc" 
 xmlns:xlink="http://www.w3.org/1999/xlink" 
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>noise-levels</Name>
    <UserStyle>
      <Title>Noise classification by levels</Title>
      <Abstract>To be complete</Abstract>
      <FeatureTypeStyle>
        <Rule>
          <Name>rule01</Name>
             
                    <Title>0..45</Title>
          <MaxScaleDenominator>50000</MaxScaleDenominator>
                    <Filter>
                      <PropertyIsLessThan>
                        <PropertyName>accuracy</PropertyName>
                        <Literal>15</Literal>
                      </PropertyIsLessThan>
                    </Filter>
            <PointSymbolizer>
              <Graphic>
                <Mark>
                  <WellKnownName>circle</WellKnownName>
                  <Fill>
                    <CssParameter name="fill">#000000</CssParameter>
                  </Fill>
                </Mark>
              <Size>6</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>