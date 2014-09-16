package wpmcn.gate.hadoop;

import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * A Hadoop job that runs GATE applications
 *
 * This job runs an archived GATE application on text files with one document per line. It produces sequence files
 * containing an XML representation the document annotation. of The GATE application is a archive file with an
 * application .xgapp file in its root directory. This application is copied to HDFS and placed into the distributed
 * cache.
 * <p>
 * The first positional argument is the GATE application. Subsequent positional arguments are input directories,
 * except for the final positional argument, which is the output directory. A sample command might be
 * <p>
 * <code>
 *    gate.hadoop jar Hadoop-GATE-1.0.jar wpmcn.gate.hadoop.HadoopGATE ANNIE.zip input output
 * </code>
 */
public class HadoopGATE extends Configured implements Tool {
   static public class HadoopGATEMapper extends Mapper<LongWritable, Text, LongWritable, Text> {

      private Text annotation = new Text();
      private String line;

      @Override
      protected void setup(Context context) throws IOException, InterruptedException {
      }

      @Override
      protected void map(LongWritable offset, Text text, Context context) throws IOException, InterruptedException {
         String xml = "";
         try {
            //xml = gate.xmlAnnotation(text.toString());
        	 URL url = new URL("http://localhost:8080/base/annotation");
        	 HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        	 connection.setRequestMethod("GET");

             //Get Response	
             BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
             while ((line = rd.readLine()) != null) {
                 xml += line;
              }
              rd.close();
         }
         finally{}
         
         annotation.set(xml);
         context.write(offset, annotation);
      }

      @Override
      protected void cleanup(Context context) throws IOException, InterruptedException {
      }
   }

   /**
    * Create a job that runs a GATE application.
    *
    * @param configuration the job configuration
    * @param localGateApp path to an archived GATE application in the local file system
    * @param hdfsGateApp HDFS path to which the GATE application will be copied
    * @param inputs HDFS input directories
    * @param output HDFS output directory
    * @return Hadoop job to run
    * @throws IOException
 * @throws URISyntaxException 
    */
   static public Job createJob(Configuration configuration,
                               Path localGateApp, Path hdfsGateApp,
                               Collection<Path> inputs, Path output) throws IOException, URISyntaxException {
      // Put the GATE application into the distributed cache.
      FileSystem fs = FileSystem.get(configuration);
      fs.copyFromLocalFile(localGateApp, hdfsGateApp);
      
      //DistributedCache.addCacheArchive(hdfsGateApp.toUri(), configuration);
      DistributedCache.createSymlink(configuration);
      DistributedCache.addCacheArchive(new URI(hdfsGateApp+"#gateApp"), configuration);

      Job job = new Job(configuration, "GATE " + output);
      for (Path input : inputs)
         FileInputFormat.addInputPath(job, input);
      if (null != output)
         SequenceFileOutputFormat.setOutputPath(job, output);

      job.setJarByClass(HadoopGATE.class);
      job.setInputFormatClass(TextInputFormat.class);
      job.setOutputFormatClass(SequenceFileOutputFormat.class);

      job.setMapperClass(HadoopGATEMapper.class);

      return job;
   }

   public int run(String[] args) throws Exception {
      // The first positional argument is a path to the archived GATE application.
      Path localGateApp = new Path(args[0]);
      // The subsequent positional arguments are input directories.
      List<Path> inputs = new ArrayList<Path>();
      int n = args.length;
      for (int i = 1 ; i < n - 1;i++)
         inputs.add(new Path(args[i]));
      // The last positional argument is the output directory.
      Path output =  n > 2 ? new Path(args[n-1]):null;
      // Copy the GATE application to a unique temporary HDFS directory.
      Path hdfsGateApp = new Path("/tmp/gate-" + UUID.randomUUID() + "-app.zip");

      Job job = createJob(getConf(), localGateApp, hdfsGateApp, inputs, output);
      boolean success = job.waitForCompletion(true);
      if (success)
         FileSystem.get(job.getConfiguration()).deleteOnExit(hdfsGateApp);
      return success ? 0 : -1;
   }

   static public void main(String[] args) throws Exception {
      System.exit(ToolRunner.run(new HadoopGATE(), args));
   }
}
