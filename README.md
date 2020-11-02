# Another Term Shell Plugin - Location

<https://play.google.com/store/apps/details?id=green_green_avk.anothertermshellplugin_location>

Returns last known location or tracks it.

## Output format
<pre>&lt;timestamp&gt; &lt;latitude_deg&gt;,&lt;longitude_deg&gt; &lt;accuracy_m&gt; &lt;altitude_m&gt; &lt;accuracy_m&gt; &lt;bearing_deg&gt; &lt;accuracy_deg&gt; &lt;speed_mps&gt; &lt;accuracy_mps&gt; &lt;provider&gt;</pre>
or<br/>
<code>No data</code> yet.</p>

## Arguments
<ul type="none">
<li><code>-b</code> &#x2014; use binary format (native endianness) instead as<pre>
struct __attribute__((__packed__)) location {
uint32_t length; // size of this structure minus 4
char[4] provider;
sint64_t time_stamp; // seconds since the beginning of the epoch
double latitude; // degrees
double longitude; // degrees
double altitude; // meters
float accuracy; // meters
float altitude_accuracy; // meters
float bearing; // degrees
float bearing_accuracy; // degrees
float speed; // meters per second
float speed_accuracy; // meters per second
};
</pre>or only the <code>length</code> field equals zero if there is no data yet.</li>
<li><code>-r</code> &#x2014; fancy output.</li>
<li><code>-t [&lt;minIntervalSeconds&gt; [&lt;minDistanceMeters&gt;]]</code> &#x2014;
output location updates continuously and exit on any signal.
<p>Defaults:<br/>
<code>&lt;minIntervalSeconds&gt;</code> is <code>1.0</code>;<br/>
<code>&lt;minDistanceMeters&gt;</code> is <code>1.0</code>.</p></li>
</ul>

## Note
**Note:** It is supposed to explicitly start the location plugin when it is required.
The <kbd>Start/Pause</kbd> button resides under the plugin launcher icon.
