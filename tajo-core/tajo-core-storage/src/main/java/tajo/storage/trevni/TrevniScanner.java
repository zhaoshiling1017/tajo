/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tajo.storage.trevni;

import org.apache.hadoop.conf.Configuration;
import org.apache.trevni.ColumnFileReader;
import org.apache.trevni.ColumnValues;
import org.apache.trevni.avro.HadoopInput;
import tajo.catalog.Schema;
import tajo.datum.BytesDatum;
import tajo.datum.DatumFactory;
import tajo.storage.FileScanner;
import tajo.storage.Fragment;
import tajo.storage.Tuple;
import tajo.storage.VTuple;

import java.io.IOException;
import java.nio.ByteBuffer;

public class TrevniScanner extends FileScanner {
  private ColumnFileReader reader;
  private int [] projIds;
  private boolean first = true;
  private ColumnValues [] columns;

  public TrevniScanner(Configuration conf, Schema schema,
                       Fragment fragment, Schema target) throws IOException {
    super(conf, schema, new Fragment[] {fragment});
    reader = new ColumnFileReader(new HadoopInput(fragment.getPath(), conf));

    projIds = new int[target.getColumnNum()];
    int tid;
    for (int i = 0; i < target.getColumnNum(); i++) {
      tid = schema.getColumnIdByName(target.getColumn(i).getColumnName());
      projIds[i] = tid;
    }
  }

  @Override
  public long getNextOffset() throws IOException {
    return 0;
  }

  public void seek(long offset) throws IOException {
  }

  @Override
  public Tuple next() throws IOException {
    Tuple tuple = new VTuple(schema.getColumnNum());

    if (first) {
      columns = new ColumnValues[projIds.length];

      for (int i = 0; i < projIds.length; i++) {
        columns[i] = reader.getValues(projIds[i]);
      }

      first = false;
    }
    if (!columns[0].hasNext()) {
      return null;
    }
    for (int i = 0; i < projIds.length; i++) {
      columns[i].startRow();
      switch (schema.getColumn(i).getDataType()) {
        case BOOLEAN:
          tuple.put(projIds[i],
              DatumFactory.createBool(((Integer)columns[i].nextValue()).byteValue()));
          break;
        case BYTE:
          tuple.put(projIds[i],
              DatumFactory.createByte(((Integer)columns[i].nextValue()).byteValue()));
          break;
        case CHAR:
          tuple.put(projIds[i],
              DatumFactory.createChar(((Integer)columns[i].nextValue()).byteValue()));
          break;

        case SHORT:
          tuple.put(projIds[i],
              DatumFactory.createShort(((Integer)columns[i].nextValue()).shortValue()));
          break;
        case INT:
          tuple.put(projIds[i],
              DatumFactory.createInt((Integer)columns[i].nextValue()));
          break;

        case LONG:
          tuple.put(projIds[i],
              DatumFactory.createLong((Long)columns[i].nextValue()));
          break;

        case FLOAT:
          tuple.put(projIds[i],
              DatumFactory.createFloat((Float)columns[i].nextValue()));
          break;

        case DOUBLE:
          tuple.put(projIds[i],
              DatumFactory.createDouble((Double)columns[i].nextValue()));
          break;

        case IPv4:
          tuple.put(projIds[i],
              DatumFactory.createIPv4(((ByteBuffer) columns[i].nextValue()).array()));
          break;

        case STRING:
        case STRING2:
          tuple.put(projIds[i],
              DatumFactory.createString((String) columns[i].nextValue()));
          break;

        case BYTES:
          tuple.put(projIds[i],
              new BytesDatum(((ByteBuffer) columns[i].nextValue())));
          break;

        default:
          throw new IOException("Unsupport data type");
      }
    }

    return tuple;
  }

  @Override
  public void reset() throws IOException {

  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
