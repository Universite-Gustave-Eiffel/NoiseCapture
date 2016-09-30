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
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>0</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>45</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#4CC800</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule02</Name>
                    <Title>45..50</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>45</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>50</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#55FF00</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule03</Name>
                    <Title>50..55</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>50</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>55</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#B9FF73</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule04</Name>
                    <Title>55..60</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>55</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>60</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#FFFF00</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule05</Name>
                    <Title>60..65</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>60</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>65</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#FFAA00</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule06</Name>
                    <Title>65..70</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>65</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>70</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#FF0000</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule07</Name>
                    <Title>70..75</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>70</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>75</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#D500FF</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
<Rule>
          <Name>rule08</Name>
                    <Title>> 75</Title>
                    <Filter>
                        <And>
                            <PropertyIsGreaterThanOrEqualTo>
                                <PropertyName>mean_leq</PropertyName>
                                <ogc:Literal>75</ogc:Literal>
                            </PropertyIsGreaterThanOrEqualTo>
                            <PropertyIsLessThan>
                                <PropertyName>mean_leq</PropertyName>
                                <Literal>150</Literal>
                            </PropertyIsLessThan>
                        </And>
                    </Filter>
            <PolygonSymbolizer>
                  <Fill>
                    <CssParameter name="fill">#960064</CssParameter>
					<CssParameter name="fill-opacity">0.5</CssParameter>
                  </Fill>
          </PolygonSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
