package net.sf.l2j.gameserver.instancemanager.custom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.UserInfo;

public class BalancerEdit
{
	private static final Set<String> VALID_STATS = new HashSet<>(Arrays.asList(
		"patk",
		"matk",
		"pdef",
		"mdef",
		"acc",
		"ev",
		"patksp",
		"matksp",
		"hp",
		"mp"
	));

	/**
	 * Adiciona ou remove um valor de balanceamento.
	 *
	 * @param stat coluna que será alterada
	 * @param classId ID da classe
	 * @param value valor da operação
	 * @param add true para adicionar e false para remover
	 * @return true caso tenha sido salvo com sucesso
	 */
	public static boolean editStat(String stat, int classId, int value, boolean add)
	{
		if (stat == null)
			return false;

		stat = stat.toLowerCase().trim();

		if (!VALID_STATS.contains(stat))
		{
			System.err.println("[BalancerEdit] Invalid stat: " + stat);
			return false;
		}

		if (!BalanceLoad.isValidClassId(classId))
		{
			System.err.println("[BalancerEdit] Invalid class ID: " + classId);
			return false;
		}

		/*
		 * Evita operação invertida caso o HTML envie um número negativo.
		 */
		value = Math.abs(value);

		final int operationValue = add ? value : -value;
		int newValue = 0;

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			con.setAutoCommit(false);

			try
			{
				/*
				 * Cria a linha da classe caso ainda não exista.
				 *
				 * Se a linha já existir, os valores atuais são mantidos.
				 */
				try (PreparedStatement insert = con.prepareStatement(
					"INSERT IGNORE INTO balance " +
					"(class_id, acc, ev, patk, matk, pdef, mdef, hp, mp, matksp, patksp) " +
					"VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)"))
				{
					insert.setInt(1, classId);
					insert.executeUpdate();
				}

				/*
				 * Atualiza diretamente no banco.
				 *
				 * O nome da coluna é seguro porque foi validado
				 * anteriormente na lista VALID_STATS.
				 */
				try (PreparedStatement update = con.prepareStatement(
					"UPDATE balance SET " + stat + " = " + stat + " + ? WHERE class_id = ?"))
				{
					update.setInt(1, operationValue);
					update.setInt(2, classId);

					final int updatedRows = update.executeUpdate();

					if (updatedRows != 1)
					{
						throw new IllegalStateException(
							"Balance row was not updated for class ID " + classId
						);
					}
				}

				/*
				 * Lê novamente o valor salvo para atualizar a memória.
				 */
				try (PreparedStatement select = con.prepareStatement(
					"SELECT " + stat + " FROM balance WHERE class_id = ?"))
				{
					select.setInt(1, classId);

					try (ResultSet rset = select.executeQuery())
					{
						if (!rset.next())
						{
							throw new IllegalStateException(
								"Balance row not found after update for class ID " + classId
							);
						}

						newValue = rset.getInt(stat);
					}
				}

				con.commit();
			}
			catch (Exception e)
			{
				try
				{
					con.rollback();
				}
				catch (Exception rollbackException)
				{
					System.err.println("[BalancerEdit] Error while rolling back transaction.");
					rollbackException.printStackTrace();
				}

				throw e;
			}
			finally
			{
				try
				{
					con.setAutoCommit(true);
				}
				catch (Exception e)
				{
					// Não é necessário interromper a operação neste ponto.
				}
			}
		}
		catch (Exception e)
		{
			System.err.println("[BalancerEdit] Error while saving balance stat.");
			System.err.println("[BalancerEdit] Class ID: " + classId);
			System.err.println("[BalancerEdit] Stat: " + stat);
			System.err.println("[BalancerEdit] Operation value: " + operationValue);

			e.printStackTrace();
			return false;
		}

		/*
		 * Atualiza o valor carregado em memória.
		 */
		BalanceLoad.setValue(stat, classId, newValue);

		/*
		 * Atualiza os jogadores online que utilizam essa classe.
		 */
		refreshPlayersOfClass(classId);

		System.out.println(
			"[BalancerEdit] Saved balance: classId=" + classId +
			", stat=" + stat +
			", operation=" + operationValue +
			", newValue=" + newValue
		);

		return true;
	}

	private static void refreshPlayersOfClass(int classId)
	{
		for (L2PcInstance player : L2World.getInstance().getAllPlayers().values())
		{
			if (player == null)
				continue;

			if (player.getClassId() == null)
				continue;

			if (player.getClassId().getId() != classId)
				continue;

			player.broadcastUserInfo();
			player.sendPacket(new UserInfo(player));
		}
	}

	public static void sendBalanceWindow(int classId, L2PcInstance player)
	{
		if (player == null)
			return;

		if (!BalanceLoad.isValidClassId(classId))
		{
			player.sendMessage("Invalid balance class ID: " + classId);
			return;
		}

		final NpcHtmlMessage htm = new NpcHtmlMessage(0);
		htm.setFile("data/html/admin/balance/balance.htm");

		htm.replace("%classId%", String.valueOf(classId));
		htm.replace("%Patk%", String.valueOf(BalanceLoad.getPAtk(classId)));
		htm.replace("%Matk%", String.valueOf(BalanceLoad.getMAtk(classId)));
		htm.replace("%Pdef%", String.valueOf(BalanceLoad.getPDef(classId)));
		htm.replace("%Mdef%", String.valueOf(BalanceLoad.getMDef(classId)));
		htm.replace("%Acc%", String.valueOf(BalanceLoad.getAccuracy(classId)));
		htm.replace("%Eva%", String.valueOf(BalanceLoad.getEvasion(classId)));
		htm.replace("%AtkSp%", String.valueOf(BalanceLoad.getPAtkSpd(classId)));
		htm.replace("%CastSp%", String.valueOf(BalanceLoad.getMAtkSpd(classId)));
		htm.replace("%Hp%", String.valueOf(BalanceLoad.getHP(classId)));
		htm.replace("%Mp%", String.valueOf(BalanceLoad.getMP(classId)));

		player.sendPacket(htm);
	}
}