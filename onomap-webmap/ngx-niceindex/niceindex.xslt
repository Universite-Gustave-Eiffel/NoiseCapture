<?xml version="1.0" encoding="UTF-8" ?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="html" encoding="UTF-8" />
  <xsl:variable name="months" select="'  JanFebMarAprMayJunJulAugSepOctNovDec'"/>

  <xsl:template match="/">
    <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE html&gt;</xsl:text>

    <html lang="en">
      <head>
        <title>Index of <xsl:value-of select="$dir"/></title>

        <meta name="version" content="1.1.0"/>
        <meta name="viewport" content="initial-scale=1, minimum-scale=1, shrink-to-fit=no, width=device-width"/>

        <link rel="stylesheet" href="/.niceindex/css/main.css"/>
        <style>
          .sortable-table {
          width: 100%;
          border-collapse: collapse;
          font-family: monospace;
          }
          .sortable-table th {
          background-color: #f5f5f5;
          padding: 8px;
          text-align: left;
          cursor: pointer;
          border: 1px solid #ddd;
          user-select: none;
          }
          .sortable-table th:hover {
          background-color: #e9e9e9;
          }
          .sortable-table td {
          padding: 4px 8px;
          border: 1px solid #eee;
          }
          .sortable-table tr:nth-child(even) {
          background-color: #f9f9f9;
          }
          .sort-indicator {
          float: right;
          margin-left: 10px;
          }
          .sort-asc::after {
          content: ' ▲';
          }
          .sort-desc::after {
          content: ' ▼';
          }
          .dir {
          font-weight: bold;
          }
        </style>
      </head>
      <body>
        <div class="container">
          <h1 align="center">
            <img class="img-responsive" style="display: inline;"
                 src="http://noise-planet.org/assets/img/logos/Logo_noisecapture.png" alt="Logo NoiseCapture"
                 height="80px" width="80px"/> NoiseCapture
          </h1>
          <h1>
            Directory index of <xsl:value-of select="$dir"/>
          </h1>
          <hr />

          <table class="sortable-table" id="fileTable">
            <thead>
              <tr>
                <th onclick="sortTable(0, 'text')" style="width: 50%">
                  Name <span class="sort-indicator"></span>
                </th>
                <th onclick="sortTable(1, 'date')" style="width: 25%">
                  Last Modified <span class="sort-indicator"></span>
                </th>
                <th onclick="sortTable(2, 'size')" style="width: 25%">
                  Size <span class="sort-indicator"></span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><a href="../" class="dir">Parent Directory</a></td>
                <td>-</td>
                <td>-</td>
              </tr>
              <xsl:apply-templates/>
            </tbody>
          </table>

          <hr />
          <footer/>
        </div>

        <script>
          <![CDATA[
          let currentSort = { column: -1, direction: 'asc' };

          function sortTable(columnIndex, dataType) {
            const table = document.getElementById('fileTable');
            const tbody = table.querySelector('tbody');
            const rows = Array.from(tbody.querySelectorAll('tr'));

            // Skip the parent directory row
            const parentRow = rows.shift();

            // Determine sort direction
            if (currentSort.column === columnIndex) {
              currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
            } else {
              currentSort.direction = 'asc';
            }
            currentSort.column = columnIndex;

            // Clear all sort indicators
            document.querySelectorAll('th .sort-indicator').forEach(indicator => {
              indicator.className = 'sort-indicator';
            });

            // Set current sort indicator
            const currentHeader = document.querySelectorAll('th')[columnIndex];
            const indicator = currentHeader.querySelector('.sort-indicator');
            indicator.className = `sort-indicator sort-${currentSort.direction}`;

            // Sort rows
            rows.sort((a, b) => {
              const aCell = a.cells[columnIndex];
              const bCell = b.cells[columnIndex];
              let aValue, bValue;

              if (dataType === 'size') {
                aValue = parseSizeToBytes(aCell.textContent.trim());
                bValue = parseSizeToBytes(bCell.textContent.trim());
                return currentSort.direction === 'asc' ? aValue - bValue : bValue - aValue;
              } else if (dataType === 'date') {
                aValue = parseDate(aCell.textContent.trim());
                bValue = parseDate(bCell.textContent.trim());
                return currentSort.direction === 'asc' ? aValue - bValue : bValue - aValue;
              } else {
                // Text sort - directories first, then files
                const aIsDir = a.cells[0].querySelector('a').classList.contains('dir');
                const bIsDir = b.cells[0].querySelector('a').classList.contains('dir');

                if (aIsDir && !bIsDir) return -1;
                if (!aIsDir && bIsDir) return 1;

                aValue = aCell.textContent.trim().toLowerCase();
                bValue = bCell.textContent.trim().toLowerCase();

                if (currentSort.direction === 'asc') {
                  return aValue.localeCompare(bValue);
                } else {
                  return bValue.localeCompare(aValue);
                }
              }
            });

            // Rebuild table
            tbody.appendChild(parentRow);
            rows.forEach(row => tbody.appendChild(row));
          }

          function parseSizeToBytes(sizeStr) {
            if (sizeStr === '-') return -1;

            const match = sizeStr.match(/^([\d.]+)([BKMG]?)$/);
            if (!match) return 0;

            const value = parseFloat(match[1]);
            const unit = match[2];

            switch (unit) {
              case 'K': return value * 1024;
              case 'M': return value * 1024 * 1024;
              case 'G': return value * 1024 * 1024 * 1024;
              default: return value;
            }
          }

          function parseDate(dateStr) {
            if (dateStr === '-') return 0;

            // Parse format: DD-MMM-YYYY HH:MM
            const parts = dateStr.split(' ');
            if (parts.length !== 2) return 0;

            const datePart = parts[0].split('-');
            const timePart = parts[1];

            if (datePart.length !== 3) return 0;

            const day = parseInt(datePart[0]);
            const monthStr = datePart[1];
            const year = parseInt(datePart[2]);

            const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                           'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
            const month = months.indexOf(monthStr);

            const date = new Date(year, month, day);
            const timeMatch = timePart.match(/(\d+):(\d+)/);
            if (timeMatch) {
              date.setHours(parseInt(timeMatch[1]), parseInt(timeMatch[2]));
            }

            return date.getTime();
          }
        ]]>
        </script>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="directory">
    <tr>
      <td>
        <a href="{current()}/" class="dir">
          <xsl:value-of select="."/>
        </a>
      </td>
      <td>
        <xsl:call-template name="timestamp">
          <xsl:with-param name="std-time" select="@mtime" />
        </xsl:call-template>
      </td>
      <td>-</td>
    </tr>
  </xsl:template>

  <xsl:template match="file">
    <tr>
      <td>
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
        </a>
      </td>
      <td>
        <xsl:call-template name="timestamp">
          <xsl:with-param name="std-time" select="@mtime" />
        </xsl:call-template>
      </td>
      <td>
        <xsl:call-template name="size">
          <xsl:with-param name="bytes" select="@size" />
        </xsl:call-template>
      </td>
    </tr>
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
