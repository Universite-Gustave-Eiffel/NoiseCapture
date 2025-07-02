<?xml version="1.0" encoding="UTF-8" ?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html" encoding="UTF-8" />
  <xsl:variable name="months" select="'  JanFebMarAprMayJunJulAugSepOctNovDec'"/>

  <xsl:template match="/">
    <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE html&gt;</xsl:text>

    <html lang="en">
      <head>
        <title>niceindex of <xsl:value-of select="$dir"/></title>

        <meta name="version" conent="1.1.0"/>
        <meta name="viewport" conent="initial-scale=1, minimum-scale=1, shrink-to-fit=no, width=device-width"/>

        <link rel="stylesheet" href=".niceindex/css/main.css"/>
      </head>
      <body>
        <div class="container">
          <h1>Directory index of <xsl:value-of select="$dir"/></h1>
          <hr />
          <pre>
            <xsl:text>&#xd;</xsl:text>
            <p>   name                          last modified              size</p>
            <a href="../" class="dir">Parent Directory</a>
            <xsl:apply-templates/>
          </pre>
          <hr />
          <footer/>
        </div>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="directory">
    <a href="{current()}/" class="dir"><xsl:value-of select="."/>
      <xsl:value-of select="substring('                              ', 1, 30-string-length())"/>
      <xsl:call-template name="timestamp"><xsl:with-param name="std-time" select="@mtime" /></xsl:call-template>
      <xsl:text>              -</xsl:text>
    </a>
  </xsl:template>

  <xsl:template match="file">
    <a href="{current()}">
      <!--Class logic -->
      <xsl:attribute name="class">
        <xsl:variable name="ext" select="translate (substring-after(., '.'), '.7', '')"/>
        <xsl:choose>
          <xsl:when test="string-length($ext) &lt; 1">
            <xsl:value-of select="."/>
          </xsl:when>
          <xsl:when test="$ext = 'z'">
            <xsl:value-of select="'zzzz'"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="$ext"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:value-of select="."/>
      <xsl:value-of select="substring('                              ', 1, 30-string-length())"/>
      <xsl:call-template name="timestamp"><xsl:with-param name="std-time" select="@mtime" /></xsl:call-template>
      <xsl:variable name="humsize"><xsl:call-template name="size"><xsl:with-param name="bytes" select="@size" /></xsl:call-template></xsl:variable>
      <xsl:value-of select="concat (substring('               ', 1, 15-string-length($humsize)), $humsize)"/>
    </a>
  </xsl:template>

  <xsl:template name="size">
    <xsl:param name="bytes"/>
    <xsl:choose>
      <xsl:when test="$bytes &lt; 1000"><xsl:value-of select="format-number($bytes, '0.0')" />B</xsl:when>
      <xsl:when test="$bytes &lt; 1048576"><xsl:value-of select="format-number($bytes div 1024, '0.0')" />K</xsl:when>
      <xsl:when test="$bytes &lt; 1073741824"><xsl:value-of select="format-number($bytes div 1048576, '0.0')" />M</xsl:when>
      <xsl:otherwise><xsl:value-of select="format-number(($bytes div 1073741824), '0.00')" />G</xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="timestamp">
    <xsl:param name="std-time" />
    <xsl:value-of select="concat(substring($std-time, 9, 2), '-', substring($months, substring($std-time, 6, 2) * 3, 3), '-', substring($std-time, 1, 4), ' ', substring($std-time, 12, 5))"/>
  </xsl:template>

</xsl:stylesheet>
