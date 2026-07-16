package net.sf.l2j.gameserver.instancemanager.custom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.L2DatabaseFactory;

public class BalanceLoad
{
	private static final int FIRST_CLASS_ID = 88;
	private static final int LAST_CLASS_ID = 118;
	private static final int SIZE = (LAST_CLASS_ID - FIRST_CLASS_ID) + 1;

	private static final String INSERT_CLASS_ROW =
		"INSERT IGNORE INTO balance " +
		"(class_id, acc, ev, patk, matk, pdef, mdef, hp, mp, matksp, patksp) " +
		"VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)";

	private static final String SELECT_BALANCE =
		"SELECT class_id, acc, ev, patk, matk, pdef, mdef, hp, mp, matksp, patksp " +
		"FROM balance WHERE class_id BETWEEN ? AND ? ORDER BY class_id";

	public static final int[] Evasion = new int[SIZE];
	public static final int[] Accuracy = new int[SIZE];
	public static final int[] PAtk = new int[SIZE];
	public static final int[] MAtk = new int[SIZE];
	public static final int[] PDef = new int[SIZE];
	public static final int[] MDef = new int[SIZE];
	public static final int[] HP = new int[SIZE];
	public static final int[] MP = new int[SIZE];
	public static final int[] MAtkSpd = new int[SIZE];
	public static final int[] PAtkSpd = new int[SIZE];

	public static void load()
	{
		clear();

		int loadedClasses = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			/*
			 * Cria automaticamente as linhas das classes 88 até 118.
			 *
			 * INSERT IGNORE não altera linhas que já existem.
			 * Portanto, os valores já configurados não serão apagados.
			 */
			createMissingRows(con);

			try (PreparedStatement statement = con.prepareStatement(SELECT_BALANCE))
			{
				statement.setInt(1, FIRST_CLASS_ID);
				statement.setInt(2, LAST_CLASS_ID);

				try (ResultSet rset = statement.executeQuery())
				{
					while (rset.next())
					{
						final int classId = rset.getInt("class_id");

						if (!isValidClassId(classId))
							continue;

						final int index = getIndex(classId);

						Accuracy[index] = rset.getInt("acc");
						Evasion[index] = rset.getInt("ev");
						PAtk[index] = rset.getInt("patk");
						MAtk[index] = rset.getInt("matk");
						PDef[index] = rset.getInt("pdef");
						MDef[index] = rset.getInt("mdef");
						HP[index] = rset.getInt("hp");
						MP[index] = rset.getInt("mp");
						MAtkSpd[index] = rset.getInt("matksp");
						PAtkSpd[index] = rset.getInt("patksp");

						loadedClasses++;
					}
				}
			}

			System.out.println("BalanceLoad: loaded " + loadedClasses + " class balances.");
		}
		catch (Exception e)
		{
			System.err.println("BalanceLoad: error while loading balance table.");
			e.printStackTrace();
		}
	}

	/**
	 * Cria automaticamente uma linha para todas as classes configuráveis.
	 *
	 * As linhas já existentes são preservadas pelo INSERT IGNORE.
	 */
	private static void createMissingRows(Connection con) throws Exception
	{
		int createdRows;

		try (PreparedStatement statement = con.prepareStatement(INSERT_CLASS_ROW))
		{
			for (int classId = FIRST_CLASS_ID; classId <= LAST_CLASS_ID; classId++)
			{
				statement.setInt(1, classId);
				statement.addBatch();
			}

			final int[] results = statement.executeBatch();

			createdRows = 0;

			for (int result : results)
			{
				if (result > 0)
					createdRows += result;
			}
		}

		if (createdRows > 0)
		{
			System.out.println(
				"BalanceLoad: automatically created " +
				createdRows +
				" missing class balance rows."
			);
		}
	}

	/**
	 * Garante que uma classe específica exista na tabela.
	 *
	 * Este método também é utilizado pelo editor.
	 */
	public static boolean ensureClassRow(int classId)
	{
		if (!isValidClassId(classId))
			return false;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(INSERT_CLASS_ROW))
		{
			statement.setInt(1, classId);
			statement.executeUpdate();

			return true;
		}
		catch (Exception e)
		{
			System.err.println(
				"BalanceLoad: error while creating balance row for class " +
				classId +
				"."
			);

			e.printStackTrace();
			return false;
		}
	}

	public static int getAccuracy(int classId)
	{
		return isValidClassId(classId) ? Accuracy[getIndex(classId)] : 0;
	}

	public static int getEvasion(int classId)
	{
		return isValidClassId(classId) ? Evasion[getIndex(classId)] : 0;
	}

	public static int getPAtk(int classId)
	{
		return isValidClassId(classId) ? PAtk[getIndex(classId)] : 0;
	}

	public static int getMAtk(int classId)
	{
		return isValidClassId(classId) ? MAtk[getIndex(classId)] : 0;
	}

	public static int getPDef(int classId)
	{
		return isValidClassId(classId) ? PDef[getIndex(classId)] : 0;
	}

	public static int getMDef(int classId)
	{
		return isValidClassId(classId) ? MDef[getIndex(classId)] : 0;
	}

	public static int getHP(int classId)
	{
		return isValidClassId(classId) ? HP[getIndex(classId)] : 0;
	}

	public static int getMP(int classId)
	{
		return isValidClassId(classId) ? MP[getIndex(classId)] : 0;
	}

	public static int getMAtkSpd(int classId)
	{
		return isValidClassId(classId) ? MAtkSpd[getIndex(classId)] : 0;
	}

	public static int getPAtkSpd(int classId)
	{
		return isValidClassId(classId) ? PAtkSpd[getIndex(classId)] : 0;
	}

	public static boolean isValidClassId(int classId)
	{
		return classId >= FIRST_CLASS_ID && classId <= LAST_CLASS_ID;
	}

	public static int getIndex(int classId)
	{
		return classId - FIRST_CLASS_ID;
	}

	public static void setValue(String stat, int classId, int value)
	{
		if (stat == null || !isValidClassId(classId))
			return;

		final int index = getIndex(classId);

		switch (stat.toLowerCase())
		{
			case "acc":
				Accuracy[index] = value;
				break;

			case "ev":
				Evasion[index] = value;
				break;

			case "patk":
				PAtk[index] = value;
				break;

			case "matk":
				MAtk[index] = value;
				break;

			case "pdef":
				PDef[index] = value;
				break;

			case "mdef":
				MDef[index] = value;
				break;

			case "hp":
				HP[index] = value;
				break;

			case "mp":
				MP[index] = value;
				break;

			case "matksp":
				MAtkSpd[index] = value;
				break;

			case "patksp":
				PAtkSpd[index] = value;
				break;
		}
	}

	public static int getValue(String stat, int classId)
	{
		if (stat == null || !isValidClassId(classId))
			return 0;

		switch (stat.toLowerCase())
		{
			case "acc":
				return getAccuracy(classId);

			case "ev":
				return getEvasion(classId);

			case "patk":
				return getPAtk(classId);

			case "matk":
				return getMAtk(classId);

			case "pdef":
				return getPDef(classId);

			case "mdef":
				return getMDef(classId);

			case "hp":
				return getHP(classId);

			case "mp":
				return getMP(classId);

			case "matksp":
				return getMAtkSpd(classId);

			case "patksp":
				return getPAtkSpd(classId);
		}

		return 0;
	}

	public static Map<String, Integer> getAllStats(int classId)
	{
		final Map<String, Integer> map = new HashMap<>();

		map.put("patk", getPAtk(classId));
		map.put("matk", getMAtk(classId));
		map.put("pdef", getPDef(classId));
		map.put("mdef", getMDef(classId));
		map.put("acc", getAccuracy(classId));
		map.put("ev", getEvasion(classId));
		map.put("patksp", getPAtkSpd(classId));
		map.put("matksp", getMAtkSpd(classId));
		map.put("hp", getHP(classId));
		map.put("mp", getMP(classId));

		return map;
	}

	private static void clear()
	{
		for (int i = 0; i < SIZE; i++)
		{
			Accuracy[i] = 0;
			Evasion[i] = 0;
			PAtk[i] = 0;
			MAtk[i] = 0;
			PDef[i] = 0;
			MDef[i] = 0;
			HP[i] = 0;
			MP[i] = 0;
			MAtkSpd[i] = 0;
			PAtkSpd[i] = 0;
		}
	}
}