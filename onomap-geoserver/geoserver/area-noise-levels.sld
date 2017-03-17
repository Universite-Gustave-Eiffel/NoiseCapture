<?xml version="1.0" encoding="UTF-8"?>
<sld:StyledLayerDescriptor version="1.0.0"
xmlns:sld="http://www.opengis.net/sld"
xmlns:ogc="http://www.opengis.net/ogc"
xmlns:gml="http://www.opengis.net/gml"
xmlns:xlink="http://www.w3.org/1999/xlink"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
	<sld:NamedLayer>
		<sld:Name>noisecapture_area</sld:Name>
		<sld:UserStyle>
			<sld:Title>Noise classification by levels</sld:Title>
			<Abstract>To be complete</Abstract>
			<sld:FeatureTypeStyle>
								<sld:Rule>
					<sld:Name>30 - 35 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>30 - 35 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>30</ogc:Literal>
							</ogc:PropertyIsGreaterThanOrEqualTo>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>35</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>
					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#82a6ad</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>35 - 40 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>35 - 40 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>35</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>40</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#a0babf</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>40 - 45  dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>40 - 45  dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>40</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>45</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#b8d6d1</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>45 - 50 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>45 - 50 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>45</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>50</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#cee4cc</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>50 - 55 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>50 - 55 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>50</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>55</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#e2f2bf</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>55 - 60 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>55 - 60 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>55</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>60</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#f3c683</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>60 - 65 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>60 - 65 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>60</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>65</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#e87e4d</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>65 - 70 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>65 - 70 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>65</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>70</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#cd463e</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>70 - 75 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>70 - 75 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>70</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>75</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#a11a4d</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>75 - 80 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>75 - 80 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>75</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>80</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#75085c</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
				<sld:Rule>
					<sld:Name>> 80 dB(A)</sld:Name>
					<sld:Description>
						<sld:Title>> 80 dB(A)</sld:Title>
					</sld:Description>
					<ogc:Filter xmlns:ogc="http://www.opengis.net/ogc">
						<ogc:And>
							<ogc:PropertyIsGreaterThan>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>80</ogc:Literal>
							</ogc:PropertyIsGreaterThan>
							<ogc:PropertyIsLessThanOrEqualTo>
								<ogc:PropertyName>la50</ogc:PropertyName>
								<ogc:Literal>120</ogc:Literal>
							</ogc:PropertyIsLessThanOrEqualTo>
						</ogc:And>
					</ogc:Filter>

					<sld:MaxScaleDenominator>36000</sld:MaxScaleDenominator>
					<sld:PolygonSymbolizer>
						<sld:Fill>
							<CssParameter name="fill-opacity">0.7</CssParameter>
							<sld:CssParameter name="fill">#430a4a</sld:CssParameter>
						</sld:Fill>
					</sld:PolygonSymbolizer>
				</sld:Rule>
			</sld:FeatureTypeStyle>
		</sld:UserStyle>
	</sld:NamedLayer>
</sld:StyledLayerDescriptor>
