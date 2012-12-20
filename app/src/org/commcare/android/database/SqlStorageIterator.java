/**
 * 
 */
package org.commcare.android.database;

import java.util.Iterator;

import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.services.storage.StorageModifiedException;

import android.database.Cursor;

/**
 * @author ctsims
 *
 */
public class SqlStorageIterator<T extends Persistable> implements IStorageIterator, Iterator<T> {

	Cursor c;
	SqlIndexedStorageUtility<T> storage;
	boolean isClosedByProgress = false;

	public SqlStorageIterator(Cursor c, SqlIndexedStorageUtility<T> storage) {
		this.c = c;
		this.storage = storage;
		c.moveToFirst();
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#hasMore()
	 */
	public boolean hasMore() {
		if(!c.isClosed()) {
			return !c.isAfterLast();
		}  else {
			if(isClosedByProgress) {
				return false;
			} else {
				//If we didn't close the cursor as part of the iterator, it means that it
				//was forcibly invalidated externally, fail accordingly.
				throw new StorageModifiedException("Storage Iterator [" + storage.table + "]" + " was invalidated");
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextID()
	 */
	public int nextID() {
		int id = c.getInt(c.getColumnIndexOrThrow(DbUtil.ID_COL));
		c.moveToNext();
		if(c.isAfterLast()) {
			c.close();
			isClosedByProgress = true;
		}
		return id;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#nextRecord()
	 */
	public T nextRecord() {
		return storage.read(nextID());
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IStorageIterator#numRecords()
	 */
	public int numRecords() {
		return c.getCount();
	}

	public boolean hasNext() {
		return hasMore();
	}

	public T next() {
		return nextRecord();
	}

	public void remove() {
		//Unsupported for now
	}

	public int peekID() {
		int id = c.getInt(c.getColumnIndexOrThrow(DbUtil.ID_COL));
		return id;
	}

}
