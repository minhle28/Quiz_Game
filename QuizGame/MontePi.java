package hadoop.examples;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class MontePi {

    public static class Pi_Mapper
            extends Mapper<Object, Text, IntWritable, DoubleWritable> {        
       
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            /*
             * Called by each Mapper node.
             * Each time called will random N point x,y from 0.0 to 1.0
             * if Inbound, write a pair <Total_point,1>
             * Key input contain "Number_Of_point,Total_point"
             */
            Random rnd = new Random();
            String parameter[] = value.toString().split(",");
            int Number_Of_point = Integer.parseInt(parameter[0]);
            int Total_point = Integer.parseInt(parameter[1]);
            for (int i = 0; i < Number_Of_point; i++) {
                double x = rnd.nextDouble();
                double y = rnd.nextDouble();
                // Check if point in circle
                if (x * x + y * y <=1.0) {
                    // In bound
                    context.write(new IntWritable(Total_point), new DoubleWritable(1));
                } 
            }
            
        }
    }

    public static class Pi_Reducer
            extends Reducer<IntWritable, DoubleWritable, IntWritable, DoubleWritable> {
        private double inBound = 0;

        public void reduce(
                IntWritable key, Iterable<DoubleWritable> values,
                Context context) throws IOException, InterruptedException {
            /*
             * Only one Key value = Total_points. => Reducer =1
             * Mssion Sum All value (1) from Mappers
             * Caculate and write out <Total_points, Pi_Value>
             */
            for (DoubleWritable val : values) {
                // Sum all value from all Mappers
                inBound += val.get();
            }
            // Calculate Pi value and sent to output file
            int total_points = key.get();
            double pi = (inBound / total_points) * 4;
            context.write(new IntWritable(total_points), new DoubleWritable(pi));
        }

    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: MontePi <number of mapper> <num of Points>");
            System.exit(2);
        }
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Estimate Pi");
        job.setJarByClass(MontePi.class);
        job.setMapperClass(Pi_Mapper.class);
        job.setReducerClass(Pi_Reducer.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(DoubleWritable.class);
        
        // get number of point
        int number_mapper = Integer.parseInt(args[0]);
        int total_points = Integer.parseInt(args[1]);

        System.out.println("Creating input file");
        // create input file for mapper
        Path inputFile = new Path("/tmp/pi/mapper_input");
        FileSystem fs = FileSystem.get(conf);
        if (fs.exists(inputFile)) {
            fs.delete(inputFile, true);
        }
        BufferedWriter writer;
        int sliper = total_points / number_mapper;
        int x = 1;
        // n-1 mapper
        for (x = 1; x < number_mapper; x++) {
            // Create number of file = number_mapper
            writer = new BufferedWriter(new OutputStreamWriter(
                    fs.create(new Path(inputFile + "/" + x + ".txt"), true)));
            writer.write(sliper+","+ total_points + "\n"); // each file have one line for slipper point and total point.
            writer.close();
        }
        // Final Mapper
        writer = new BufferedWriter(new OutputStreamWriter(
                fs.create(new Path(inputFile + "/" + x + ".txt"), true)));
        writer.write((total_points - (x - 1) * sliper)+","+ total_points + "\n"); // write the rest points.
        writer.close();
        System.out.println("Creating input file done.");

        // Seting job
        FileInputFormat.addInputPath(job, inputFile);
        job.setNumReduceTasks(1);

        // create output file for reducer
        Path outputDir = new Path("/tmp/pi/recuder_output");
        if (fs.exists(outputDir)) {
            fs.delete(outputDir, true);
        }
        FileOutputFormat.setOutputPath(job, outputDir);
        if (job.waitForCompletion(true)) {
            // Scan all output file.
            FileStatus[] files = fs.listStatus(outputDir);
            for (FileStatus file : files) {                
                if (file.isFile() && file.getPath().getName().startsWith("part-")) {
                    FSDataInputStream inputStream = fs.open(file.getPath());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line = reader.readLine();
                    while (line != null) {
                        String tmp[] = line.split("\t");
                        System.out.println("Estimate Pi is " + tmp[1] + " with " + tmp[0] + " simulations.");
                        line = reader.readLine();
                    }
                }
            }           
        } else {
            System.out.println("Error in computing !!!");
        }
        // clean up

    }

}
// clean package assembly:single