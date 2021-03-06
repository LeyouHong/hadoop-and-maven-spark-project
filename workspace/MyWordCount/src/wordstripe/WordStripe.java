package wordstripe;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.LongWritable;
/**
 * Created by hongleyou on 2017/4/13.
 */
public class WordStripe {

    public static class MyReducer extends Reducer<Text, MapWritable, Text, Text> {

        @Override
        protected void reduce(Text item, Iterable<MapWritable> values,
                              Reducer<Text, MapWritable, Text, Text>.Context context)
                throws IOException, InterruptedException {

            HashMap<String, Integer> smap = new HashMap<String, Integer>();
            int total = 0;
            for (MapWritable v : values) {
                for (Entry<Writable, Writable> entry : v.entrySet()) {
                    if (smap.containsKey(entry.getKey().toString())) {
                        int t = smap.get(entry.getKey().toString());
                        smap.put(entry.getKey().toString(), t+ ((IntWritable)entry.getValue()).get());
                    } else {
                        smap.put(entry.getKey().toString(), ((IntWritable)entry.getValue()).get());
                    }
                    total += ((IntWritable) entry.getValue()).get();
                }
            }

            HashMap<String, String> lmap = new HashMap<String, String>();
            for (Entry<String, Integer> entry : smap.entrySet()) {
                String r = entry.getValue() + "/" + total;
                lmap.put(entry.getKey(), r);
            }
            String s = "";
            for(String k : lmap.keySet()) {
                s += "| ("+k+", "+lmap.get(k)+") |";
            }
            context.write(new Text(item), new Text(s));
        }
    }

    public static class MyMapper extends Mapper<LongWritable, Text, Text, MapWritable> {

        HashMap<String, MapWritable> hashmap;

        @Override
        protected void setup(
                Mapper<LongWritable, Text, Text, MapWritable>.Context context)
                throws IOException, InterruptedException {
            hashmap = new HashMap<String, MapWritable>();
        }

        @Override
        protected void map(LongWritable key, Text value,
                           Mapper<LongWritable, Text, Text, MapWritable>.Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            String[] strs = line.split("\\s+");

            for (int i = 0; i < strs.length; i++) {
                for (int j = i + 1; j < strs.length && !strs[i].equals(strs[j]); j++) {

                    MapWritable mapWritable = hashmap.get(strs[i]);
                    if (mapWritable == null) {
                        mapWritable = new MapWritable();
                        hashmap.put(strs[i], mapWritable);
                    }

                    if (mapWritable.get(new Text(strs[j])) == null) {
                        mapWritable.put(new Text(strs[j]), new IntWritable(1));
                    } else {
                        IntWritable inWritable = (IntWritable) mapWritable
                                .get(new Text(strs[j]));
                        mapWritable.put(new Text(strs[j]), new IntWritable(
                                inWritable.get() + 1));
                    }
                }
            }
        }

        @Override
        protected void cleanup(
                Mapper<LongWritable, Text, Text, MapWritable>.Context context)
                throws IOException, InterruptedException {
            super.cleanup(context);
            for (Entry<String, MapWritable> entry : hashmap.entrySet()) {
                context.write(new Text(entry.getKey()), entry.getValue());
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Stripe");
        job.setJarByClass(WordStripe.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        job.setMapperClass(MyMapper.class);
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setMapOutputValueClass(MapWritable.class);
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
